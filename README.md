# Workout Tracker

A sleek Android fitness app built with Jetpack Compose to help you track workouts, monitor progress, and achieve your fitness goals.

## ☁️ Cloud Sync

- **Firebase Firestore**: Cross-device data synchronization
- **Authentication**: Firebase Auth for user accounts
- **Conflict Resolution**: UUID-based to prevent conflicts
- **Offline Support**: Local-first with background sync
- **Network Monitoring**: Automatic sync when online

## 📦 Permissions

- `READ_EXTERNAL_STORAGE`: Import data
- `WRITE_EXTERNAL_STORAGE`: Export data
- `INTERNET`: Cloud sync
- `ACCESS_NETWORK_STATE`: Network monitoringerial 3, dynamic colors, and accessibility in mind.

# ✨ Features

- **Real-Time Workout Tracking**: Start sessions with an integrated timer and log exercises on the go.
- **Comprehensive Exercise Management**: Add custom exercises with sets, reps, weights, and notes.
- **Progress Analytics**: Visualize your fitness journey with detailed graphs and statistics.
- **Workout History**: Review past sessions with full details and performance insights.
- **Personal Records**: Track PRs, total volume, and exercise-specific stats.
- **Workout Templates**: Create and reuse custom workout templates for quick starts.
- **Cloud Sync**: Sync workouts and templates across devices with Firebase Firestore.
- **Theme Support**: Switch between light, dark, and system themes.
- **Import/Export**: CSV import/export for workouts and templates, compatible with Strong app.
- **Edit Past Workouts**: Modify completed workouts for accuracy.
- **Modern UI**: Material Design 3 interface that's intuitive and visually appealing.
- **Offline-First**: Store data locally with Room database—no internet required.

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (SQLite)
- **Navigation**: Jetpack Navigation Compose
- **Sync**: Firebase Firestore
- **Dependency Injection**: Manual DI
- **Build Tool**: Gradle (Kotlin DSL)

### Key Dependencies

- AndroidX Core KTX
- Compose BOM
- Material 3 Components
- Navigation Compose
- Room Persistence Library
- Lifecycle Components
- Firebase Auth & Firestore

## 🚀 Quick Start

### Requirements

- Android Studio (latest stable)
- Minimum SDK: API 34 (Android 14)
- Target SDK: API 36 (Android 16)
- Kotlin 2.0.21+
- JDK 11+

### Setup

1. **Clone the repo**:
   ```bash
   git clone https://github.com/flandolf/Workout.git
   cd Workout
   ```

2. **Open in Android Studio**:
   - Launch Android Studio
   - Select "Open" and choose the project directory

3. **Sync and Build**:
   - Gradle will sync automatically
   - Build the project

4. **Run on Device/Emulator**:
   - Connect a device or start an emulator
   - Hit "Run" (▶️)

## � Project Structure

```
app/src/main/java/com/flandolf/workout/
├── data/                    # Data Layer
│   ├── AppDatabase.kt      # Room DB setup
│   ├── Entities.kt         # DB models (workouts, templates)
│   ├── FormatWeight.kt     # Weight formatting utilities
│   ├── Volume.kt           # Volume calculation helpers
│   ├── WorkoutDao.kt       # Data access for workouts
│   ├── WorkoutRepository.kt # Workout data repository
│   ├── TemplateDao.kt      # Data access for templates
│   ├── TemplateRepository.kt # Template data repository
│   └── sync/               # Cloud sync layer
│       ├── AuthRepository.kt    # Firebase Auth handling
│       ├── FirestoreModels.kt   # Firestore data models
│       ├── NetworkMonitor.kt    # Network connectivity
│       └── SyncRepository.kt    # Sync logic
├── ui/                     # UI Layer
│   ├── components/         # Reusable UI components
│   │   ├── BarChart.kt         # Progress bar chart
│   │   ├── BottomNavigationBar.kt # Main navigation
│   │   ├── EmptyStateCard.kt   # Empty state displays
│   │   └── ProgressGraph.kt    # Progress visualization
│   ├── screens/           # Compose screens
│   │   ├── EditTemplateScreen.kt   # Template editor
│   │   ├── EditWorkoutScreen.kt    # Workout editor
│   │   ├── ExerciseDetailScreen.kt # Exercise stats
│   │   ├── GraphDetailScreen.kt    # Detailed graphs
│   │   ├── HistoryScreen.kt        # Workout history
│   │   ├── ProgressScreen.kt       # Overall progress
│   │   ├── SettingsScreen.kt       # App settings
│   │   ├── SyncSettingsScreen.kt   # Sync configuration
│   │   ├── TemplateScreen.kt       # Template management
│   │   └── WorkoutScreen.kt        # Active workout
│   ├── theme/             # App theming
│   │   ├── Color.kt       # Color definitions
│   │   ├── Theme.kt       # Theme setup
│   │   ├── ThemeMode.kt   # Theme mode enum
│   │   └── Type.kt        # Typography
│   └── viewmodel/         # ViewModels
│       ├── EditWorkoutViewModel.kt # Edit workout logic
│       ├── HistoryViewModel.kt     # History data
│       ├── SyncViewModel.kt        # Sync state management
│       ├── TemplateViewModel.kt    # Template operations
│       ├── ThemeViewModel.kt       # Theme management
│       └── WorkoutViewModel.kt     # Active workout logic
└── MainActivity.kt        # App entry point with navigation
```

## 🏗 Architecture

Follows MVVM pattern for clean separation:

- **Model**: Entities and repositories (local + sync)
- **View**: Compose UI screens
- **ViewModel**: State management and logic

Data flows: UI → ViewModel → Repository → DAO/Database, with sync to Firestore.

## 🔧 Build & Run

### Commands

```bash
# Clean
./gradlew clean

# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

APKs are in `app/build/outputs/apk/`.

## 📊 Database

Room-based schema:

- **Workout**: Session data (date, duration, timestamps)
- **ExerciseEntity**: Exercise-workout links with position
- **SetEntity**: Set details (reps, weight)
- **Template**: Reusable workout templates
- **TemplateExerciseEntity**: Template exercise links
- **TemplateSetEntity**: Template set details
- Relations: Workout/Template → Exercises → Sets

## 🎨 Screens

1. **Workout**: Active tracking with timer and templates
2. **History**: Past sessions with edit options
3. **Progress**: Overall analytics with exercise details
4. **Templates**: Manage and create workout templates
5. **Settings**: App config, sync, import/export, themes

Built with Material 3, dynamic colors, and accessibility in mind.

## � Permissions

- `READ_EXTERNAL_STORAGE`: Import data
- `WRITE_EXTERNAL_STORAGE`: Export data

## 🤝 Contributing

1. Fork the repo
2. Create a branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -m 'Add feature'`
4. Push: `git push origin feature/your-feature`
5. Open a PR

## 📄 License

MIT License - see [LICENSE](LICENSE).

## 🙏 Credits

Powered by Jetpack Compose, Material Design 3, Room, Firebase, and Kotlin. Thanks to the Android community!

## 🆘 Help

- Check [Issues](https://github.com/flandolf/Workout/issues)
- Report bugs with device info and steps
