# Personal Assistant Chatbot

This repository contains a Java Spring Boot backend in [`backend/`](backend/). It manages tasks, schedule
conflicts, free time and daily briefings. General conversation on `POST /api/chat` uses Google's Gemini API;
task creation and schedule actions remain validated Java operations.

## Run the backend on Windows

Open PowerShell in the project folder and run:

```powershell
cd backend
mvn clean test
$secureKey = Read-Host "Enter your Gemini API key" -AsSecureString
$env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new("", $secureKey).Password
mvn spring-boot:run
```

Create a key in [Google AI Studio](https://aistudio.google.com/apikey) and enter it only at the secure
PowerShell prompt. Never add the key to a source file or commit it. The key must be set again in each new
PowerShell window. The API defaults to the stable `gemini-3.8-flash` model. More setup details and API examples
are in [`backend/README.md`](backend/README.md).