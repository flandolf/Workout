# Workout Tracker

A modern Android fitness application built with Jetpack Compose for tracking workouts, exercises, and fitness progress.

## 📱 Features

- **Workout Tracking**: Start and track workout sessions with built-in timer
- **Exercise Management**: Add exercises with sets, reps, and weights
- **Progress Visualization**: View progress graphs and statistics for each exercise
- **Workout History**: Browse past workouts with detailed information
- **Exercise Statistics**: Track total reps, sets, volume, and personal records
- **Material Design 3**: Modern, intuitive user interface
- **Offline Support**: Local data storage with Room database

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with ViewModels
- **Database**: Room with SQLite
- **Navigation**: Navigation Compose
- **Dependency Injection**: Manual dependency injection
- **Build System**: Gradle with Kotlin DSL

### Dependencies

- **AndroidX Core KTX**: Core Android extensions
- **Compose BOM**: Latest Compose components
- **Material 3**: Modern Material Design components
- **Navigation Compose**: Screen navigation
- **Room**: Local database persistence
- **Lifecycle**: Lifecycle-aware components

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Latest stable version (recommended: Arctic Fox or later)
- **Minimum SDK**: API 34 (Android 14)
- **Target SDK**: API 36 (Android 16)
- **Kotlin**: 2.0.21
- **Java**: JDK 11

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/flandolf/Workout.git
   cd Workout
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned directory and select it

3. **Sync Project**
   - Android Studio will automatically sync the project with Gradle
   - Wait for all dependencies to download

4. **Run the App**
   - Connect an Android device or start an emulator
   - Click the "Run" button (green play icon) in Android Studio
   - Select your target device/emulator

## 📁 Project Structure

```
app/src/main/java/com/flandolf/workout/
├── data/                    # Data layer
│   ├── AppDatabase.kt      # Room database configuration
│   ├── Entities.kt         # Database entities and relationships
│   ├── WorkoutDao.kt       # Data access objects
│   ├── WorkoutRepository.kt # Repository pattern implementation
│   └── CommonExercises.kt  # Predefined exercise data
├── ui/                     # UI layer
│   ├── screens/           # Compose screens
│   │   ├── WorkoutScreen.kt
│   │   ├── HistoryScreen.kt
│   │   ├── ExerciseDetailScreen.kt
│   │   └── ProgressScreen.kt
│   ├── theme/             # App theming
│   └── viewmodel/         # ViewModels
│       ├── WorkoutViewModel.kt
│       └── HistoryViewModel.kt
└── MainActivity.kt        # Main activity with navigation
```

## 🏗️ Architecture

The app follows the **MVVM (Model-View-ViewModel)** architectural pattern:

- **Model**: Data entities and repository classes
- **View**: Compose UI components and screens
- **ViewModel**: Business logic and state management

### Data Flow

1. **UI** triggers actions in **ViewModel**
2. **ViewModel** interacts with **Repository**
3. **Repository** handles data operations with **Room DAO**
4. **LiveData/Flow** updates propagate back to **UI**

## 🔧 Build Configuration

### Build Types

- **Debug**: Development build with debugging enabled
- **Release**: Production build with code optimization and obfuscation

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest
```

## 📊 Database Schema

The app uses Room with the following entities:

- **Workout**: Stores workout sessions with date and duration
- **ExerciseEntity**: Links exercises to workouts
- **SetEntity**: Stores individual sets with reps and weight
- **Relationships**: Workout → Exercises → Sets (one-to-many)

## 🎨 UI Components

### Key Screens

1. **Workout Screen**: Active workout tracking with timer
2. **History Screen**: List of past workouts
3. **Exercise Detail Screen**: Detailed statistics and progress for specific exercises
4. **Progress Screen**: Overall fitness progress visualization

### Design System

- **Material 3**: Latest Material Design guidelines
- **Dynamic Colors**: Adapts to system theme
- **Responsive Layout**: Optimized for various screen sizes
- **Accessibility**: Screen reader support and touch targets

## 📦 APK Generation

### Debug APK
```bash
./gradlew assembleDebug
```
Location: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK
```bash
./gradlew assembleRelease
```
Location: `app/build/outputs/apk/release/app-release.apk`

## 🔐 Permissions

The app requires the following permissions:

- **READ_EXTERNAL_STORAGE**: For importing/exporting workout data
- **WRITE_EXTERNAL_STORAGE**: For exporting workout data

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Write tests for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with ❤️ using Jetpack Compose
- Material Design 3 for beautiful UI
- Room for reliable data persistence
- Kotlin for modern Android development

## 📞 Support

If you have any questions or issues:

1. Check the [Issues](https://github.com/flandolf/Workout/issues) page
2. Create a new issue with detailed description
3. Include device information and steps to reproduce
