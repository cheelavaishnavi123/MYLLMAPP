#MyApplication3
An Android application that integrates chat, voice input, and note management features using the Llama model for command processing. The app includes user authentication, a history view for conversations, and animations for a better user experience.
Features

Chat Interface: Engage in conversations with the app, powered by the Llama model.
Voice Input: Use voice commands to interact with the app.
Note Management: Create, save, and manage notes as .txt files in the Downloads folder.
User Authentication: Login and signup system with a local database to store user credentials.
Conversation History: View past conversations with a history feature.
Animations: Smooth transitions and animations for UI elements (e.g., chat messages, login screen).

Prerequisites

Android Studio: Version 2023.1.1 or later (project uses Gradle 8.1.0.2).
Android SDK: Minimum API 21 (Android 5.0 Lollipop).

To run this project, you need to download the following model file, which is not included in the repository due to its size:

Model File: Llama-3.2-3B-Instruct-Q4_K_S.gguf (1.84 GB)
Download Link: https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/blob/main/Llama-3.2-3B-Instruct-Q4_K_S.gguf
Instructions: Place the Llama-3.2-3B-Instruct-Q4_K_S.gguf file in the app/src/main/assets/ directory.

Setup Instructions

Clone the Repository:
git clone https://github.com/cheelavaishnavi123/MYLLMAPP.git

Download the Model File:

Download the model file from the link above.
Place the Llama-3.2-3B-Instruct-Q4_K_S.gguf file in the app/src/main/assets/ directory.

Open in Android Studio:

Launch Android Studio.
Select Open and navigate to the cloned MYLLMAPP folder.

Configure the Project:

Sync the project with Gradle by clicking "Sync Project with Gradle Files".
Ensure the Android SDK is installed and configured.

Run the App:

Connect an Android device or start an emulator.
Click Run in Android Studio to build and install the app.

Project Structure

app/src/main/assets/: Directory where the Llama model file (Llama-3.2-3B-Instruct-Q4_K_S.gguf) should be placed.
app/src/main/cpp/: Native C++ code for integrating the Llama model, including libraries like libllama.so.
app/src/main/java/com/example/myapplication/: Kotlin source files for activities (e.g., MainActivity.kt, LoginActivity.kt, HistoryActivity.kt).
app/src/main/res/: Resources including layouts, drawables, and animations.

Dependencies

Gradle: Uses Kotlin DSL (build.gradle.kts).
Llama Model: Requires Llama-3.2-3B-Instruct-Q4_K_S.gguf for command processing.
Native Libraries: Includes libllama.so, libggml.so, and libc++\_shared.so for model execution.

Notes

Ensure you have at least 2 GB of storage for the model file.
Notes are saved as .txt files in the Downloads folder on the device.
Contact [cheelavaishnavi123@gmail.com] for issues with obtaining the model.

Contributing
Feel free to fork this repository, make improvements, and submit pull requests. For major changes, please open an issue to discuss your ideas.
License
This project is licensed under the MIT License. See the LICENSE file for details (if added).
Contact
For questions or support, reach out to [your email].
