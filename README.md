# 🐾 PetSense — Smart Care & Behavior Analyzer

PetSense is a native Android application designed to help pet owners organize health records, monitor wellness information, review health reports, and identify unusual patterns in recorded health data. It also includes a calendar-based health history and a computer-vision-based behavior analysis component.

The project combines Android application development, cloud data management, and Python-based computer vision in a pet-care platform.

## 📱 App Screenshots

## 📱 App Screenshots

### Main Dashboard
![PetSense Main Dashboard](assets/screenshots/main-dashboard.jpg)

### Health Reports and Insights
![PetSense Insights](assets/screenshots/insights.jpg)

### Health Anomaly Detection
![PetSense Health Anomaly Detection](assets/screenshots/analysis.jpg)

### Pet Behavior Analysis
![PetSense Behavior Analysis](assets/screenshots/behavior-analysis.jpg)

### Behavior Analysis Results
![PetSense Behavior Analysis Results](assets/screenshots/behavior-analysis-result.jpg)

### Profile Screen
![PetSense Profile](assets/screenshots/profile-screen.jpg)

## ✨ Key Features

- **User Authentication:** Account access through Firebase Authentication.
- **Pet Health Records:** Store and review available health information and observations.
- **Health Reports:** Generate health reports from recorded pet health information.
- **Health Anomaly Detection:** Analyze recorded health data to identify unusual patterns that may require attention.
- **Calendar-Based Health History:** Browse health records by date and review historical entries.
- **Health Dashboard:** View important pet wellness information through a consolidated dashboard.
- **Behavior Analysis:** Analyze pet behavior using the project's Python-based computer-vision component.

*Health insights are informational and do not replace professional veterinary diagnosis.*

## 🛠️ Technology Stack

### Android Application
- Java
- Android SDK
- XML layouts
- Android Studio
- View Binding
- Material Components
- MVVM-style architecture

### Backend and Cloud
- Firebase Authentication
- Firebase Realtime Database

### Computer Vision
- Python
- Flask
- OpenCV
- AI models used by the behavior-analysis component

## 🏗️ Architecture Overview

The Android application provides the user interface for authentication, pet health records, reports, calendar-based history, and available analysis results.

Firebase Authentication manages user sign-in, while Firebase Realtime Database supports cloud-based data storage and synchronization.

The Python/Flask computer-vision component supports behavior analysis. Its integration with the Android application depends on the configured backend communication flow.

```text
Android Application
        |
        +---- Firebase Authentication
        |
        +---- Firebase Realtime Database
        |          |
        |          +---- Pet Health Records
        |          +---- Calendar History
        |          +---- Health Reports
        |          +---- Anomaly Detection
        |
        +---- Python / Flask Backend
                   |
                   +---- OpenCV
                   +---- Behavior Analysis
```

The diagram is a conceptual overview. Adapt the data flow to match the actual implementation of each feature.

## 📂 Project Structure

The Android project is organized around application components, UI layouts, and data handling.

```text
PetSense/
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           ├── res/
│           │   ├── layout/
│           │   ├── drawable/
│           │   └── values/
│           └── AndroidManifest.xml
├── assets/
│   └── screenshots/
├── README.md
└── build.gradle
```

*The structure above is illustrative. Update it if your actual repository uses different directories or Gradle filenames.*

## 🚀 Getting Started

### Prerequisites

- Android Studio
- A compatible Android SDK
- A Java Development Kit compatible with the project's Gradle version
- A Firebase project configured for the Android application
- Python and the required dependencies if running the behavior-analysis backend

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/jordanbelfort19/petsense.git
   ```

2. Open the cloned project in Android Studio.

3. Configure Firebase for your application and add the required Android configuration file.

4. Sync the Gradle project and resolve any missing dependencies.

5. Build and run the application on a compatible Android emulator or physical device.

6. If using the behavior-analysis backend, configure and run the Python/Flask component separately according to its dependencies and endpoint configuration.

### Firebase Configuration

- Register the Android application in your Firebase project.
- Enable Firebase Authentication with the sign-in method used by the app.
- Configure Firebase Realtime Database and the required access rules.
- Keep private credentials and service-account keys out of source control.

## 🧪 Testing

Test the following workflows where implemented:

- User sign-in and authentication.
- Creating and retrieving pet health records.
- Viewing health records for selected calendar dates.
- Generating and viewing health reports.
- Detecting unusual patterns in recorded health data.
- Submitting behavior-analysis inputs and viewing results.

## ⚠️ Limitations

- Anomaly detection depends on the health data available and the implemented detection logic.
- Behavior-analysis results depend on the configured computer-vision pipeline.
- The application is an academic project and is not a substitute for professional veterinary care.

## 🔮 Future Enhancements

- More detailed health trend visualizations.
- Improved historical health analytics.
- Additional reminders and preventive-care tracking.
- Expanded behavior-analysis capabilities.
- Enhanced testing, error handling, and application security.

## 👨‍💻 Developer

**Rohit Kumar M**

- GitHub: [jordanbelfort19](https://github.com/jordanbelfort19)
- Project Repository: [PetSense](https://github.com/jordanbelfort19/petsense)

---

⭐ If you find this project interesting, consider starring the repository.
