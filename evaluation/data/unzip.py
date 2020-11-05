# unzip.py

from zipfile import ZipFile

# Extract first set of datasets
with ZipFile('datasets.zip', 'r') as zipObj:
   # Extract all the contents of zip file in current directory
   zipObj.extractall()

# Extract second set of datasets
with ZipFile('raw/raw_datasets.zip', 'r') as zipObj:
   # Extract all the contents of zip file in the raw directory
   zipObj.extractall("raw")

print("Datasets are now ready!")