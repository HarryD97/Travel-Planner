# Travel Planner: Project Documentation

## I. Introduction

In an era where travel is increasingly popular, tools for efficient travel planning and management are essential. The main goal of Travel Planner is to enhance the travel experience by creating an all-in-one solution. This app combines real-time navigation, fitness tracking, location search, trip management, and travel journaling capabilities. The project leverages modern mobile technologies—including geolocation, camera capabilities, device sensors, and external APIs—to create a powerful travel companion.

## II. Problem Statement

Travel Planner solves the inefficiency of traditional travel planning, where travelers often juggle multiple apps for route planning, hotel bookings, and travel diaries. Key problems addressed include:

- Manual route optimization leads to longer travel times and increased costs
- Difficulty creating a comprehensive travel record that combines location, photos, and personal experiences

**Target Audience:**
- Tech-savvy travelers looking for an integrated planning tool
- Travel bloggers and photographers passionate about documenting their journeys
- Fitness-conscious travelers who want to track their physical activity during trips

## III. Design and Implementation

### Application Design and Architecture

Travel Planner is a native Android app developed using Kotlin and follows the Model-View-ViewModel (MVVM) architecture pattern.

- **UI Layer (View)**: Composed of a series of Fragments (e.g., `MapFragment`, `DiaryFragment`) and a single `MainActivity`
- **ViewModel Layer**: Exposes data from each major functional module to the UI layer through `LiveData`
- **Data Layer**: Uses the Room Persistence Library (`TravelDatabase`) to store core user data locally on the device

### Application Features

#### Interactive Map and Route Optimization
Users can search for and mark locations on the map. The `OptimizeRoute` feature uses a combination of the nearest neighbor and 2-opt algorithms to plan the shortest path, which can be saved to the itinerary with one click.

#### Travel Diary and Editing
Users can create rich travel diaries containing text, photos, and waypoints.

#### Hotel Search
Integrated with the Google Places API to find real-time hotel information based on the user's itinerary, with sorting options for price and rating.

#### Step Counter
An integrated pedometer (`StepCounter`) allows users to record their steps during a trip via the `StepCountService`.

### APIs and Technologies

#### Native Android APIs:
- **Location Services**: To get the user's current location and for path navigation
- **CameraX**: For in-app photography
- **Sensor Services**: To access the pedometer sensor

#### External APIs:
- Google Maps SDK
- Google Places API
- Google Directions API

## IV. Minimum UI Requirements

### Clear and Intuitive Layout
Adopts Material Design 3 styling, using a `BottomNavigationView` for top-level navigation.

### Visual Appeal
A unified color scheme and icon system are applied throughout the app. Special colors are used to provide clear visual feedback for route optimization and other prompts.

### Information Feedback
All time-consuming operations (e.g., network requests, route optimization) display loading indicators. The results of operations are communicated via Toast messages or dialog boxes.

### Responsive Design
The layout extensively uses `ConstraintLayout` to ensure adaptability across different screen sizes. Pages with more content, like the `DiaryEditFragment`, use a `ScrollView` for better usability on small screens.

## V. Additional Features

Unlike simple point-A-to-point-B navigation, the app's path optimization function solves the multi-point "Traveling Salesperson Problem" (TSP). This saves valuable time and transportation costs for users who want to visit multiple attractions efficiently within a limited time.

## VI. Testing and Evaluation

The project relied on manual regression testing to validate features and user interactions. The main issues found and resolved are detailed below:

### Problem 1: Diary Record Status Lost

**Phenomenon**: After starting a diary recording and navigating to another screen, the recording status would be lost upon returning. The UI showed the recording as stopped, but the database entry was still "in progress," causing data inconsistency.

**Evaluation**: The root cause was improper Fragment lifecycle management, leading to the loss of the ViewModel instance and its state when the Fragment was rebuilt.

**Solution**: The `DiaryViewModel` instance's scope was elevated to the Activity level. This binds the ViewModel's lifecycle to the Activity, ensuring state persistence across Fragment recreations. UI state restoration logic was also added to the `onResume` lifecycle callback.

### Problem 2: Unstable Hardware Interaction (Camera and Positioning)

**Phenomenon**: The camera would fail to start on the first attempt and required navigating away and back to activate. The positioning feature would often get stuck "acquiring location information."

**Evaluation**: The camera issue stemmed from timing problems between initialization and permission requests. The positioning issue was due to relying solely on a single provider (GPS) without handling cases of weak signals or GPS being disabled.

**Solution**: The permission request process was refactored using the modern `ActivityResultContracts` API to ensure the camera is initialized only after permissions are granted. For positioning, a multi-provider approach (using both GPS and network) was implemented, and fetching the "last known location" was added as a quick fallback, significantly improving success rates and response times.

### Problem 3: Incomplete Diary List Data

**Phenomenon**: The diary list page failed to display associated data like step count, number of route points, and photo count, showing "0" for each.

**Evaluation**: Database queries revealed that the initial query (`DiaryRepository.allDiaries`) only returned basic diary information without joining the associated tables for waypoints and photos.

**Solution**: A new query method, `getAllDiariesWithDetails()`, was added to `DiaryDao` to load a diary with all its related data in a single query. The ViewModel and Fragment were updated to use this new method, resolving the data display issue.

### Problem 4: UI Does Not Refresh After Operations

**Phenomenon**: After deleting a diary from its details page, the entry was still visible upon returning to the list page. Additionally, the list of photos was not cleared after finishing a diary recording.

**Evaluation**: This was identified as a synchronization issue between the data layer and the UI layer. The database was updated correctly, but the UI was not being told to repaint.

**Solution**: A proactive notification strategy was adopted. After a successful deletion, `loadAllDiariesWithDetails()` is called immediately to force a refresh of the list data. For the photo list, `photosAdapter.submitList(emptyList())` is called directly within the `stopCurrentDiary` method to clear the UI instantly.

## VII. Conclusion

This project successfully delivered a comprehensive Android application for travel planning, achieving its core goal of providing a one-stop tool for planning, recording, and exploration. Through continuous iteration, the project addressed challenges ranging from architecture and API integration to user experience. This underscored the lesson that a successful application requires not only powerful features but also meticulous attention to detail and a relentless focus on the user experience.

### Future Outlook

- **Collaborative Planning**: Introduce real-time, multi-user itinerary editing
- **Deeper API Integration**: Integrate flight search/booking APIs and enable in-app payments
- **Personalized Recommendations**: Intelligently recommend destinations and activities based on user history and preferences

## VIII. Figma

[View the Figma Design File]([https://www.figma.com/design/your-design-file](https://www.figma.com/design/Mdv5Rz0UHtKcKBNvFuLz48/Travel-Planner?t=3llXJuFHvKhYcAS7-0))

## IX. Demo Video

[https://youtu.be/inJshbLBL9g](https://youtu.be/inJshbLBL9g)

## X. References

- [Android Jetpack Official Documentation](https://developer.android.com/jetpack)
- [Google Maps Platform Official Documentation](https://developers.google.com/maps)
- [Google Places API Official Documentation](https://developers.google.com/maps/documentation/places)
- [Kotlin Coroutines Official Documentation](https://kotlinlang.org/docs/coroutines-overview.html)
- [Material Design 3 Official Guide](https://m3.material.io/)

## XI. Appendices

**Code Repository**: [https://github.com/HarryD97/Travel-Planner](https://github.com/HarryD97/Travel-Planner)
