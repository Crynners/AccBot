import zipfile
import os

source_dir = r'C:\Users\vitne\source\repos\AccBot\SoloMinator\clean_publish'
zip_path = r'C:\Users\vitne\source\repos\AccBot\SoloMinator\clean_deploy.zip'

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, files in os.walk(source_dir):
        for file in files:
            file_path = os.path.join(root, file)
            arcname = os.path.relpath(file_path, source_dir).replace('\\', '/')
            zipf.write(file_path, arcname)

print('Zip created successfully')
