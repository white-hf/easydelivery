# EasyDelivery

**EasyDelivery** is a package delivery Android app designed to be efficient, user-friendly, and capable of functioning without a network connection. With a clean and intuitive interface, EasyDelivery can be easily extended to support various courier services.

## Why I Created This Project

During my experience as a part-time package delivery driver, I encountered significant challenges with the apps provided by courier companies. These apps often had the following issues:

1. **Network Dependency**: The apps heavily relied on network connectivity, making numerous unnecessary backend API calls each time a UI was accessed. This caused slow operations and made the app unusable in areas with poor network coverage, such as inside buildings.
   
2. **Complex UI Design**: The user interface was overly complicated. Submitting a delivery task required switching between multiple screens, wasting valuable time during deliveries.
   
3. **Battery Drain**: The app frequently and periodically retrieved GPS information, which quickly drained the device's battery.

**EasyDelivery** addresses these issues by offering a more streamlined, efficient, and reliable solution for package delivery.

## Technologies Applied

- **Modular Design**: The app is built using a modular architecture, making it easy to extend and maintain.
- **Room Database**: Offline data storage is managed using the Room database, allowing the app to function without an active network connection.
- **Multi-threading**: The app utilizes multiple threads to manage tasks efficiently, ensuring a smooth user experience even when handling complex operations.

## How to Install and Run the Project

1. Clone the repository from GitHub:
   ```bash
   git clone https://github.com/white-hf/easydelivery.git
