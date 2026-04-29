# # train.py — Bi-Weekly Automated Retraining Pipeline
# # Loads the cleaned dataset, re-engineers features, tunes XGBoost with Optuna,
# # evaluates on a held-out test set, and saves date-stamped model artifacts.
# # Schedule this script to run every 2 weeks via Windows Task Scheduler.
#
# import os
# import sys
# import logging
# import warnings
# import json
# from datetime import datetime
#
# import numpy as np
# import pandas as pd
# import joblib
# import optuna
# from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
# from sklearn.model_selection import TimeSeriesSplit
# from sklearn.preprocessing import LabelEncoder
# from xgboost import XGBRegressor
#
# # Suppress pandas and sklearn warnings that clutter the log output
# warnings.filterwarnings("ignore")
#
# # ── Paths ─────────────────────────────────────────────────────────────────────
#
# # Resolve all paths relative to this file so the script works from any working directory
# BASE_DIR  = os.path.dirname(os.path.abspath(__file__))
# DATA_PATH = os.path.join(BASE_DIR, "data", "last_known_data.csv")  # growing cleaned dataset
# MODEL_DIR = os.path.join(BASE_DIR, "model")                         # where .pkl files are saved
# LOG_DIR   = os.path.join(BASE_DIR, "logs")                          # where log files are saved
# LOG_CSV   = os.path.join(LOG_DIR, "training_log.csv")               # cumulative metrics across runs
#
# # Create directories if they don't exist yet (first run)
# os.makedirs(MODEL_DIR, exist_ok=True)
# os.makedirs(LOG_DIR, exist_ok=True)
#
# # ── Logging ───────────────────────────────────────────────────────────────────
#
# # Capture today's date once — used in filenames and the metrics log
# RUN_TIMESTAMP = datetime.now().strftime("%Y-%m-%d")
#
# # Write logs to both the console and a date-stamped file so every run is traceable
# logging.basicConfig(
#     level=logging.INFO,
#     format="%(asctime)s  %(levelname)s  %(message)s",
#     handlers=[
#         logging.StreamHandler(sys.stdout),
#         logging.FileHandler(os.path.join(LOG_DIR, f"train_{RUN_TIMESTAMP}.log")),
#     ],
# )
# log = logging.getLogger(__name__)
#
# # ── Constants ─────────────────────────────────────────────────────────────────
#
# # Columns that contain string categories and need label encoding before training
# CAT_COLS = ["family", "state", "store_type", "holiday_type", "locale", "city"]
#
# # Exact feature order the model was trained on — must match predict.py
# FEATURE_COLS = [
#     "store_nbr", "family", "onpromotion",
#     "state", "store_type", "cluster",
#     "dcoilwtico", "transactions",
#     "holiday_type", "locale", "transferred", "is_holiday",
#     "year", "month", "day", "day_of_week", "week_of_year",
#     "is_weekend", "month_sin", "month_cos", "week_sin", "week_cos", "half_year",
#     "sales_lag_1", "sales_lag_7", "sales_lag_14", "sales_lag_30",
#     "rolling_mean_7", "rolling_mean_14", "rolling_mean_30", "rolling_std_7",
#     "trend_7", "trend_14",
#     "promo_effect", "holiday_promo",
# ]
#
# TARGET_COL    = "sales"  # column the model is trained to predict
# OPTUNA_TRIALS = 50       # number of hyperparameter combinations Optuna will try
# TEST_RATIO    = 0.2      # last 20% of rows used for final evaluation
# RANDOM_SEED   = 42       # keeps results reproducible across runs
#
#
# # ── Step 1: Load Data ─────────────────────────────────────────────────────────
#
# def load_data(path: str) -> pd.DataFrame:
#     log.info(f"Loading data from: {path}")
#
#     # Raise a clear error early rather than letting pandas fail silently later
#     if not os.path.exists(path):
#         raise FileNotFoundError(f"Data file not found: {path}")
#
#     df = pd.read_csv(path)
#
#     # Parse the date column so dt accessors work in feature engineering
#     df["date"] = pd.to_datetime(df["date"])
#
#     log.info(f"Loaded {len(df):,} rows | {df.shape[1]} columns")
#     log.info(f"Date range: {df['date'].min().date()} → {df['date'].max().date()}")
#     return df
#
#
# # ── Step 2: Encode Categoricals ───────────────────────────────────────────────
#
# def encode_categoricals(df: pd.DataFrame) -> tuple[pd.DataFrame, dict]:
#     log.info("Encoding categorical columns...")
#     encoders = {}  # store fitted encoders so predict.py can reuse the same mappings
#
#     for col in CAT_COLS:
#         if col in df.columns:
#             le = LabelEncoder()
#
#             # Cast to str first to handle any mixed-type or NaN values safely
#             df[col] = le.fit_transform(df[col].astype(str))
#
#             # Save the fitted encoder — needed at inference time to transform new inputs
#             encoders[col] = le
#             log.info(f"  {col:15s} → {len(le.classes_)} classes")
#
#     return df, encoders
#
#
# # ── Step 3: Feature Engineering ───────────────────────────────────────────────
#
# def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
#     log.info("Engineering features...")
#
#     # Sort chronologically within each store-product pair before computing lags
#     df = df.sort_values(["store_nbr", "family", "date"]).reset_index(drop=True)
#
#     # Basic date parts extracted from the datetime column
#     df["year"]         = df["date"].dt.year
#     df["month"]        = df["date"].dt.month
#     df["day"]          = df["date"].dt.day
#     df["day_of_week"]  = df["date"].dt.dayofweek        # 0 = Monday, 6 = Sunday
#     df["week_of_year"] = df["date"].dt.isocalendar().week.astype(int)
#     df["is_weekend"]   = df["day_of_week"].isin([5, 6]).astype(int)
#     df["half_year"]    = (df["month"] > 6).astype(int)  # 1 if July-December, else 0
#
#     # Cyclical encoding wraps month and weekday onto a circle so Dec->Jan and Sun->Mon
#     # are treated as adjacent rather than as opposites at opposite ends of a linear scale
#     df["month_sin"] = np.sin(2 * np.pi * df["month"] / 12)
#     df["month_cos"] = np.cos(2 * np.pi * df["month"] / 12)
#     df["week_sin"]  = np.sin(2 * np.pi * df["day_of_week"] / 7)
#     df["week_cos"]  = np.cos(2 * np.pi * df["day_of_week"] / 7)
#
#     # Group by store + product so lags only look back within the same series
#     # Without this groupby, lag_1 for store 2 would bleed into store 1's last row
#     group = df.groupby(["store_nbr", "family"])
#
#     # Lag features give the model direct access to recent historical sales values
#     df["sales_lag_1"]  = group["sales"].shift(1)   # yesterday
#     df["sales_lag_7"]  = group["sales"].shift(7)   # same day last week
#     df["sales_lag_14"] = group["sales"].shift(14)  # same day two weeks ago
#     df["sales_lag_30"] = group["sales"].shift(30)  # same day last month
#
#     # Rolling averages smooth out noise and capture short/medium/long-term trends
#     df["rolling_mean_7"]  = group["sales"].transform(lambda x: x.rolling(7,  min_periods=1).mean())
#     df["rolling_mean_14"] = group["sales"].transform(lambda x: x.rolling(14, min_periods=1).mean())
#     df["rolling_mean_30"] = group["sales"].transform(lambda x: x.rolling(30, min_periods=1).mean())
#
#     # Rolling std captures how volatile sales have been recently
#     df["rolling_std_7"] = group["sales"].transform(lambda x: x.rolling(7, min_periods=1).std())
#
#     # Trend features show the direction and magnitude of recent momentum
#     df["trend_7"]  = df["sales_lag_1"] - df["sales_lag_7"]   # short-term momentum
#     df["trend_14"] = df["sales_lag_1"] - df["sales_lag_14"]  # medium-term momentum
#
#     # Interaction features combine two signals into one — lets the model learn
#     # that a promotion during a holiday behaves differently from either alone
#     df["promo_effect"]  = df["onpromotion"] * df["sales_lag_1"].fillna(0)
#     df["holiday_promo"] = df["onpromotion"] * df["is_holiday"]
#
#     # Rows at the start of each series will have NaN lags — fill with 0
#     df = df.fillna(0)
#
#     log.info(f"Feature engineering complete | shape: {df.shape}")
#     return df
#
#
# # ── Step 4: Train / Test Split ────────────────────────────────────────────────
#
# def split_data(df: pd.DataFrame, test_ratio: float = TEST_RATIO):
#     # Sort chronologically — never shuffle time-series data or future rows leak into training
#     df = df.sort_values(["store_nbr", "family", "year", "month", "day"]).reset_index(drop=True)
#
#     # Integer index of the boundary between train and test
#     split_idx = int(len(df) * (1 - test_ratio))
#
#     train_df = df.iloc[:split_idx]   # earlier 80% of data
#     test_df  = df.iloc[split_idx:]   # later 20% of data — never seen during training
#
#     X_train = train_df[FEATURE_COLS]
#     y_train = train_df[TARGET_COL]
#     X_test  = test_df[FEATURE_COLS]
#     y_test  = test_df[TARGET_COL]
#
#     log.info(f"Train: {len(X_train):,} rows | Test: {len(X_test):,} rows | Features: {len(FEATURE_COLS)}")
#     return X_train, X_test, y_train, y_test
#
#
# # ── Step 5: Optuna Hyperparameter Tuning ──────────────────────────────────────
#
# def run_optuna(X_train: pd.DataFrame, y_train: pd.Series, n_trials: int = OPTUNA_TRIALS) -> dict:
#     log.info(f"Starting Optuna search ({n_trials} trials, 5-fold TimeSeriesCV)...")
#
#     # Hide Optuna's per-trial output — only our log.info calls will show
#     optuna.logging.set_verbosity(optuna.logging.WARNING)
#
#     # TimeSeriesSplit respects temporal order during cross-validation
#     # so validation folds always follow training folds chronologically
#     tscv = TimeSeriesSplit(n_splits=5)
#
#     def objective(trial):
#         # Optuna suggests a hyperparameter combination to evaluate this trial
#         params = {
#             "n_estimators":     trial.suggest_int("n_estimators", 300, 1000),
#             "max_depth":        trial.suggest_int("max_depth", 3, 12),
#             "learning_rate":    trial.suggest_float("learning_rate", 0.005, 0.1, log=True),
#             "subsample":        trial.suggest_float("subsample", 0.6, 1.0),
#             "colsample_bytree": trial.suggest_float("colsample_bytree", 0.6, 1.0),
#             "min_child_weight": trial.suggest_int("min_child_weight", 1, 10),
#             "gamma":            trial.suggest_float("gamma", 0, 5),
#             "tree_method":      "hist",    # histogram-based splits — faster on large data
#             "random_state":     RANDOM_SEED,
#         }
#
#         maes = []
#         for train_idx, val_idx in tscv.split(X_train):
#             X_tr,  X_val = X_train.iloc[train_idx], X_train.iloc[val_idx]
#             y_tr,  y_val = y_train.iloc[train_idx], y_train.iloc[val_idx]
#
#             model = XGBRegressor(**params)
#             model.fit(X_tr, y_tr, verbose=False)
#
#             # Clip negatives — sales can never be below zero
#             preds = np.clip(model.predict(X_val), 0, None)
#             maes.append(mean_absolute_error(y_val, preds))
#
#         # Return mean MAE across all folds — Optuna minimises this
#         return np.mean(maes)
#
#     study = optuna.create_study(direction="minimize")
#     study.optimize(objective, n_trials=n_trials, show_progress_bar=False)
#
#     # Retrieve the winning params and add the fixed settings back in
#     best_params = study.best_params.copy()
#     best_params.update({"tree_method": "hist", "random_state": RANDOM_SEED})
#
#     log.info(f"Optuna complete | Best CV MAE: {study.best_value:.4f}")
#     log.info(f"Best params: {json.dumps(best_params, indent=2)}")
#     return best_params
#
#
# # ── Step 6: Train Final Model ─────────────────────────────────────────────────
#
# def train_final_model(X_train, y_train, best_params: dict) -> XGBRegressor:
#     log.info("Training final model on full train set...")
#
#     # Re-train on the entire training set (not just one CV fold) using the best params
#     model = XGBRegressor(**best_params)
#     model.fit(X_train, y_train, verbose=False)
#
#     log.info("Training complete.")
#     return model
#
#
# # ── Step 7: Evaluate ──────────────────────────────────────────────────────────
#
# def evaluate(model: XGBRegressor, X_test, y_test) -> dict:
#     preds  = np.clip(model.predict(X_test), 0, None)  # clip negatives before scoring
#     mae    = mean_absolute_error(y_test, preds)
#     rmse   = np.sqrt(mean_squared_error(y_test, preds))
#     r2     = r2_score(y_test, preds)
#     mean_s = float(np.mean(y_test))
#
#     metrics = {
#         "mae":        round(mae, 4),
#         "rmse":       round(rmse, 4),
#         "r2":         round(r2, 4),
#         "mean_sales": round(mean_s, 4),
#         # MAE as a % of mean sales — easier to interpret than raw MAE alone
#         "mae_pct":    round(mae / mean_s * 100, 2) if mean_s > 0 else None,
#     }
#
#     log.info("=== Evaluation Results ===")
#     log.info(f"  MAE        : {metrics['mae']}")
#     log.info(f"  RMSE       : {metrics['rmse']}")
#     log.info(f"  R2         : {metrics['r2']}")
#     log.info(f"  Mean Sales : {metrics['mean_sales']}")
#     log.info(f"  MAE/Mean   : {metrics['mae_pct']}%")
#
#     return metrics
#
#
# # ── Step 8: Save Artifacts ────────────────────────────────────────────────────
#
# def save_artifacts(model, encoders, timestamp: str):
#     # Date-stamped copies keep a full version history of every retrain
#     stamped_model_path   = os.path.join(MODEL_DIR, f"model_{timestamp}.pkl")
#     stamped_encoder_path = os.path.join(MODEL_DIR, f"encoders_{timestamp}.pkl")
#
#     # The "latest" aliases are what predict.py always loads — overwritten each run
#     latest_model_path    = os.path.join(MODEL_DIR, "model.pkl")
#     latest_encoder_path  = os.path.join(MODEL_DIR, "encoders.pkl")
#
#     joblib.dump(model,    stamped_model_path)
#     joblib.dump(encoders, stamped_encoder_path)
#     joblib.dump(model,    latest_model_path)
#     joblib.dump(encoders, latest_encoder_path)
#
#     log.info(f"Saved: {stamped_model_path}")
#     log.info(f"Saved: {stamped_encoder_path}")
#     log.info(f"Updated latest: {latest_model_path}")
#     log.info(f"Updated latest: {latest_encoder_path}")
#
#
# # ── Step 9: Log Metrics to CSV ────────────────────────────────────────────────
#
# def log_metrics(metrics: dict, best_params: dict, timestamp: str):
#     # Combine timestamp + metrics + best params into a single flat row
#     row    = {"timestamp": timestamp, **metrics, **best_params}
#     row_df = pd.DataFrame([row])
#
#     # Append to the existing log if it exists, otherwise create it fresh
#     if os.path.exists(LOG_CSV):
#         existing = pd.read_csv(LOG_CSV)
#         updated  = pd.concat([existing, row_df], ignore_index=True)
#     else:
#         updated = row_df
#
#     updated.to_csv(LOG_CSV, index=False)
#     log.info(f"Metrics logged to: {LOG_CSV}")
#
#
# # ── Main Pipeline ─────────────────────────────────────────────────────────────
#
# def main():
#     log.info("=" * 60)
#     log.info(f"  RETRAINING PIPELINE — {RUN_TIMESTAMP}")
#     log.info("=" * 60)
#
#     df = load_data(DATA_PATH)                                    # 1. load
#     df, encoders = encode_categoricals(df)                       # 2. encode
#     df = engineer_features(df)                                   # 3. features
#     X_train, X_test, y_train, y_test = split_data(df)           # 4. split
#     best_params = run_optuna(X_train, y_train, OPTUNA_TRIALS)    # 5. tune
#     model = train_final_model(X_train, y_train, best_params)     # 6. train
#     metrics = evaluate(model, X_test, y_test)                    # 7. evaluate
#     save_artifacts(model, encoders, RUN_TIMESTAMP)               # 8. save
#     log_metrics(metrics, best_params, RUN_TIMESTAMP)             # 9. log
#
#     log.info("=" * 60)
#     log.info("  PIPELINE COMPLETE")
#     log.info("=" * 60)
#
#
# if __name__ == "__main__":
#     main()