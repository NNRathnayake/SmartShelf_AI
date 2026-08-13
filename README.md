 Smart Shelf AI _ Retail Sales Forecasting System

An end-to-end machine learning system for retail sales forecasting using XGBoost, optimized with Optuna, and deployed via a Flask backend. Predictions are consumed in real-time by an Android application.

---

## 🚀 Features
- Retail sales prediction using machine learning
- Model optimization with Optuna (50+ trials)
- Achieved Mean Absolute Error (MAE): 38
- REST API built with Flask for real-time predictions
- Android app integration using Retrofit

---

## 🛠️ Tech Stack
- **Machine Learning:** Python, Scikit-learn, XGBoost, Optuna  
- **Backend:** Flask  
- **Frontend (Mobile):** Android (Java/Kotlin)  
- **Tools:** Jupyter Notebook, PyCharm  

---

## ⚙️ How It Works
1. Data is preprocessed and used to train an XGBoost model  
2. Model is optimized using Optuna  
3. Trained model is saved as a `.pkl` file  
4. Flask backend loads the model and exposes prediction APIs  
5. Android app sends requests and receives predictions in real-time  

---

## ▶️ Running the Project

### 1. Start the Backend (Flask API)
- Open the backend project in **PyCharm**
- Open terminal and run:

```bash
python src/app.py
