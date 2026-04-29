# import os
# import joblib
#
# MODEL_PATH = "model/model.pkl"
# BACKUP_PATH = "model/model_backup.pkl"
#
#
# def save_model(model):
#     """
#     Saves model safely with backup rotation.
#     """
#
#     # If existing model exists → move to backup
#     if os.path.exists(MODEL_PATH):
#         if os.path.exists(BACKUP_PATH):
#             os.remove(BACKUP_PATH)
#         os.replace(MODEL_PATH, BACKUP_PATH)
#
#     # Save new model
#     joblib.dump(model, MODEL_PATH)
#     print(" New model saved and replaced safely")
#
#
# def load_model():
#     """
#     Always loads latest model for prediction API
#     """
#     return joblib.load(MODEL_PATH)