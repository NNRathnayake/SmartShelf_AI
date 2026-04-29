import pandas as pd
import numpy as np

FEATURE_COLS = [
    'store_nbr', 'family', 'onpromotion',
    'state', 'store_type', 'cluster',
    'dcoilwtico', 'transactions',
    'holiday_type', 'locale', 'transferred', 'is_holiday',
    'year', 'month', 'day', 'day_of_week', 'week_of_year',
    'is_weekend', 'month_sin', 'month_cos', 'week_sin', 'week_cos', 'half_year',
    'sales_lag_1', 'sales_lag_7', 'sales_lag_14', 'sales_lag_30',
    'rolling_mean_7', 'rolling_mean_14', 'rolling_mean_30', 'rolling_std_7',
    'trend_7', 'trend_14',
    'promo_effect', 'holiday_promo'
]


def build_features(user_input, history_df):

    df = pd.DataFrame([user_input])
    df['date'] = pd.to_datetime(df['date'])

    # ---------------- DATE FEATURES ----------------
    df['year'] = df['date'].dt.year
    df['month'] = df['date'].dt.month
    df['day'] = df['date'].dt.day
    df['day_of_week'] = df['date'].dt.dayofweek
    df['week_of_year'] = df['date'].dt.isocalendar().week.astype(int)

    df['is_weekend'] = df['day_of_week'].isin([5, 6]).astype(int)
    df['half_year'] = (df['month'] > 6).astype(int)

    # ---------------- CYCLICAL FEATURES ----------------
    df['month_sin'] = np.sin(2 * np.pi * df['month'] / 12)
    df['month_cos'] = np.cos(2 * np.pi * df['month'] / 12)
    df['week_sin'] = np.sin(2 * np.pi * df['day_of_week'] / 7)
    df['week_cos'] = np.cos(2 * np.pi * df['day_of_week'] / 7)

    # ---------------- FILTER HISTORY ----------------
    store = user_input['store_nbr']
    family = user_input['family']

    hist = history_df[
        (history_df['store_nbr'] == store) &
        (history_df['family'] == family)
    ].sort_values('date')

    sales = hist['sales'].values

    # ---------------- SAFETY CHECK ----------------
    if len(sales) == 0:
        sales = np.array([0.0])

    # ---------------- LAGS ----------------
    def lag(n):
        return float(sales[-n]) if len(sales) >= n else float(sales[-1])

    df['sales_lag_1'] = lag(1)
    df['sales_lag_7'] = lag(7)
    df['sales_lag_14'] = lag(14)
    df['sales_lag_30'] = lag(30)

    # ---------------- ROLLING ----------------
    def rolling_mean(n):
        return float(sales[-n:].mean()) if len(sales) >= n else float(sales.mean())

    def rolling_std(n):
        return float(sales[-n:].std()) if len(sales) >= n else 0.0

    df['rolling_mean_7'] = rolling_mean(7)
    df['rolling_mean_14'] = rolling_mean(14)
    df['rolling_mean_30'] = rolling_mean(30)
    df['rolling_std_7'] = rolling_std(7)

    # ---------------- TREND ----------------
    df['trend_7'] = df['sales_lag_1'] - df['sales_lag_7']
    df['trend_14'] = df['sales_lag_1'] - df['sales_lag_14']

    # ---------------- DEFAULT FEATURES ----------------
    df['state'] = 'None'
    df['store_type'] = 'A'
    df['cluster'] = 0
    df['dcoilwtico'] = 0
    df['transactions'] = 0
    df['holiday_type'] = 'None'
    df['locale'] = 'None'
    df['transferred'] = 0
    df['is_holiday'] = 0

    # ---------------- PROMO ----------------
    df['onpromotion'] = user_input.get('onpromotion', 0)

    df['promo_effect'] = df['onpromotion'] * df['sales_lag_1']
    df['holiday_promo'] = df['onpromotion'] * df['is_holiday']

    # ---------------- FINAL ----------------
    df = df.fillna(0)

    for col in FEATURE_COLS:
        if col not in df.columns:
            df[col] = 0

    return df[FEATURE_COLS]