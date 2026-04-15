import os

fxml_dir = r'c:\Users\VishalGoswami\eclipse-workspace\Sunny_Printers\src\main\resources\fxml'

for root_dir, dirs, files in os.walk(fxml_dir):
    for filename in files:
        if filename.endswith(".fxml"):
            filepath = os.path.join(root_dir, filename)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            new_content = content.replace('mdi-circle', 'mdi-checkbox-blank-circle')
            new_content = new_content.replace('mdi-checkbox-blank-checkbox-blank-circle', 'mdi-checkbox-blank-circle')

            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                print(f"Fixed {filepath}")
