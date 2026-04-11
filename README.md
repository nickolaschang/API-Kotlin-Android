# ApiApp

Android application demonstrating API integration, MVVM architecture, and dependency injection.

## Features

- **Login Screen** — Authenticates users via the NIT3213 API using student ID (username) and first name (password)
- **Dashboard Screen** — Displays a list of entities from the API in a RecyclerView
- **Details Screen** — Shows full details of a selected entity including its description

## Architecture

The app follows MVVM (Model-View-ViewModel) with a Repository pattern:

```
com.example.apiapp/
├── data/
│   ├── model/         # Data classes (LoginRequest, LoginResponse, DashboardResponse)
│   ├── api/           # Retrofit API interface
│   └── repository/    # Repository layer wrapping API calls
├── di/                # Hilt dependency injection modules
├── ui/
│   ├── login/         # Login screen (Activity + ViewModel)
│   ├── dashboard/     # Dashboard screen (Activity + ViewModel + RecyclerView Adapter)
│   └── details/       # Details screen (Activity)
└── ApiApp.kt          # Application class for Hilt
```

## Tech Stack

- **Language**: Kotlin
- **DI**: Hilt (Dagger)
- **Networking**: Retrofit + Gson
- **Async**: Kotlin Coroutines
- **Architecture**: ViewModel + LiveData
- **UI**: Material Design Components, RecyclerView, ConstraintLayout

## API

Base URL: `https://nit3213api.onrender.com/`

| Endpoint | Method | Description |
|---|---|---|
| `/footscray/auth` | POST | Authenticate (Footscray campus) |
| `/sydney/auth` | POST | Authenticate (Sydney campus) |
| `/dashboard/{keypass}` | GET | Retrieve entity list using keypass from login |

The app allows selecting between Footscray and Sydney from a dropdown on the login screen.

### Credentials

Per the course update, the login body uses:

```json
{
  "username": "sYourStudentID",
  "password": "YourFirstName"
}
```

- **Username**: your student ID (e.g. `s12345678`)
- **Password**: your first name

## Running the App

1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Run the app on an emulator or device (min SDK 26)

## Running Tests

```bash
./gradlew testDebugUnitTest
```

Unit tests cover:
- `AppRepositoryTest` — Repository success/failure handling
- `LoginViewModelTest` — Login validation and state management
- `DashboardViewModelTest` — Entity loading and error handling
