import os
import pandas as pd
import numpy as np
import joblib

from features import build_features, FEATURE_COLS


BASE_DIR = os.path.dirname(os.path.abspath(__file__))

MODEL_PATH = os.path.join(BASE_DIR, "model", "model.pkl")
ENCODER_PATH = os.path.join(BASE_DIR, "model", "encoders.pkl")
DATA_PATH = os.path.join(BASE_DIR, "data", "last_known_data.csv")


model = joblib.load(MODEL_PATH)
encoders = joblib.load(ENCODER_PATH)

history_df = pd.read_csv(DATA_PATH)
history_df["date"] = pd.to_datetime(history_df["date"])



def predict_sales(user_input: dict):

    # 1. Build features
    df = build_features(user_input, history_df)

    # 2. Encode categoricals safely
    for col, le in encoders.items():
        if col in df.columns:
            val = str(df[col].iloc[0])

            # handle unseen categories
            if val not in le.classes_:
                val = le.classes_[0]

            df[col] = le.transform([val])[0]

    # 3. Ensure all required features exist
    for col in FEATURE_COLS:
        if col not in df.columns:
            df[col] = 0

    # 4. Reorder columns exactly as training
    df = df[FEATURE_COLS]


    print("\n FINAL FEATURE VECTOR")
    print("Shape:", df.shape)

    print("\n Feature Columns:")
    print(list(df.columns))

    print("\n First Row Values:")
    print(df.iloc[0].to_dict())

    print("\n Missing Values Check:")
    print(df.isnull().sum().sum(), "total nulls")

    # 5. Predict
    pred = model.predict(df)[0]

    return float(max(0, pred))



if __name__ == "__main__":

    sample_input = {
        "store_nbr": 2,
        "family": "BEVERAGES",
        "date": "2018-04-01",
        "onpromotion": 1
    }

    result = predict_sales(sample_input)

    print("\n TEST PREDICTION")
    print("Input :", sample_input)
    print("Output:", result)