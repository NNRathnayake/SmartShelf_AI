from flask import Flask, jsonify, request
from features import build_features, FEATURE_COLS
import pandas as pd
import joblib
import os
from datetime import date, timedelta

app = Flask(__name__)

BASE_DIR     = os.path.dirname(os.path.abspath(__file__))
DATA_PATH    = os.path.join(BASE_DIR, "data", "last_known_data.csv")
MODEL_PATH   = os.path.join(BASE_DIR, "model", "model.pkl")
ENCODER_PATH = os.path.join(BASE_DIR, "model", "encoders.pkl")

model      = joblib.load(MODEL_PATH)
encoders   = joblib.load(ENCODER_PATH)
history_df = pd.read_csv(DATA_PATH, parse_dates=["date"])


def run_prediction(user_input: dict, hist: pd.DataFrame) -> float:
    """Predict using a specific history dataframe (supports rolling forecast)."""
    df = build_features(user_input, hist)
    for col, le in encoders.items():
        if col in df.columns:
            val = str(df[col].iloc[0])
            if val not in le.classes_:
                val = le.classes_[0]
            df[col] = le.transform([val])[0]
    for col in FEATURE_COLS:
        if col not in df.columns:
            df[col] = 0
    df = df[FEATURE_COLS]
    return float(max(0, model.predict(df)[0]))


@app.route("/", methods=["GET"])
def home():
    return jsonify({"status": "running", "message": "Smart Shelf AI API is active 🚀"})


@app.route("/predict", methods=["POST"])
def predict():
    data = request.get_json()
    for key in ["store_nbr", "family", "date", "onpromotion"]:
        if key not in data:
            return jsonify({"error": f"Missing field: {key}"}), 400
    result = run_prediction(data, history_df)
    return jsonify({"predicted_sales": round(result, 2)})


@app.route("/sales-history", methods=["GET"])
def sales_history():
    store_nbr = request.args.get("store_nbr", default=1, type=int)
    family    = request.args.get("family",    default="BEVERAGES", type=str)
    filtered  = history_df[
        (history_df["store_nbr"] == store_nbr) &
        (history_df["family"]    == family)
    ].sort_values("date")
    data = [
        {"date": row["date"].strftime("%Y-%m-%d"), "sales": float(round(row["sales"], 2))}
        for _, row in filtered.iterrows()
    ]
    return jsonify({"store_nbr": store_nbr, "family": family, "data": data})


@app.route("/forecast", methods=["GET"])
def forecast():
    store_nbr = request.args.get("store_nbr", default=1,           type=int)
    family    = request.args.get("family",    default="BEVERAGES", type=str)
    days      = request.args.get("days",      default=7,           type=int)
    promotion = request.args.get("onpromotion", default=0,         type=int)

    today        = date.today()
    results      = []
    rolling_hist = history_df.copy()  # grows with each predicted day

    for i in range(1, days + 1):
        future_date = (today + timedelta(days=i)).strftime("%Y-%m-%d")

        user_input = {
            "store_nbr":   store_nbr,
            "family":      family,
            "date":        future_date,
            "onpromotion": promotion
        }

        # Pass the GROWING history so each day sees previous predictions as lags
        predicted = run_prediction(user_input, rolling_hist)

        results.append({
            "date":            future_date,
            "predicted_sales": round(predicted, 2)
        })

        # Append today's prediction so tomorrow's lag_1 = this value
        new_row = pd.DataFrame([{
            "store_nbr": store_nbr,
            "family":    family,
            "date":      pd.to_datetime(future_date),
            "sales":     predicted
        }])
        rolling_hist = pd.concat([rolling_hist, new_row], ignore_index=True)

    return jsonify({"store_nbr": store_nbr, "family": family, "forecast": results})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)