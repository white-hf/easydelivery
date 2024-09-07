# EasyDelivery

**EasyDelivery** is a package delivery Android app designed to be efficient, user-friendly, and capable of functioning without a network connection. With a clean and intuitive interface, EasyDelivery can be easily extended to support various courier services.

## Why I Created This Project

During my experience as a part-time package delivery driver, I encountered significant challenges with the apps provided by courier companies. These apps often had the following issues:

1. **Network Dependency**: The apps heavily relied on network connectivity, making numerous unnecessary backend API calls each time a UI was accessed. This caused slow operations and made the app unusable in areas with poor network coverage, such as inside buildings.
   
2. **Complex UI Design**: The user interface was overly complicated. Submitting a delivery task required switching between multiple screens, wasting valuable time during deliveries.
   
3. **Battery Drain**: The app frequently and periodically retrieved GPS information, which quickly drained the device's battery.

**EasyDelivery** addresses these issues by offering a more streamlined, efficient, and reliable solution for package delivery.

## Technologies Applied

- **Modular Design**:  
  The app is built using a modular architecture, making it easy to extend and maintain. This design allows EasyDelivery to support multiple courier services seamlessly.

- **Room Database**:  
  Offline data storage is managed using the Room database, enabling the app to function without an active network connection. Before starting a delivery, the app retrieves all necessary information and stores it locally, eliminating the need for backend API calls during delivery.

- **Log-Structured File System Inspired Design**:  
  The app's design is inspired by log-structured file systems. During delivery, all data related to delivered tasks are first stored in the local database. Separate threads handle the uploading of this data to the backend server, employing a retry strategy to ensure successful uploads even in the face of network issues.

- **Multi-threading**:  
  The app utilizes multiple threads to manage tasks efficiently, ensuring a smooth user experience even when handling complex operations such as data synchronization and uploads.

- **Data Synchronization Strategy**:  
  To handle potential network issues, the app implements a strategy that retries data uploads until they succeed, ensuring data consistency and reliability.

- **Data Security and Consistency Considerations**:
  - **Data Security**: Once a package is delivered and its data is successfully uploaded, the app deletes the local data to mitigate security risks.
  - **Data Consistency**: There is a risk that key information might be modified during delivery, potentially affecting delivery accuracy. To reduce this risk, a lightweight data synchronization API is used to ensure data integrity.
![](https://github.com/white-hf/blog/blob/main/img/easydelivery.jpg)
## How to Install and Run the Project

1. **Clone the repository from GitHub**:
    ```bash
    git clone https://github.com/white-hf/easydelivery.git
    ```
2. **Open the project in Android Studio**.
3. **Build the project** using the Android Studio build tools.
4. **Run the app** on an emulator or physical device.

## How to Use the Project

1. **Login**: Start by logging into the app using your courier service credentials.
2. **Package List**: Once logged in, you'll see a list of packages assigned to you for delivery.
3. **Offline Mode**: You can access and manage your deliveries even when offline. The app syncs data once the network is available.
4. **Delivery Status**: Update the status of each delivery as you complete it.
5. **Battery Optimization**: The app intelligently manages GPS updates to conserve battery life.

## Future Enhancements

- **Support for Multiple Courier Services**:  
  The app architecture allows easy integration of multiple courier services, making it adaptable to various delivery systems.

- **Enhanced Offline Capabilities**:  
  Future updates will improve offline data handling, allowing for even more robust performance without network dependency.

- **Advanced Data Synchronization**:  
  Implement more sophisticated synchronization algorithms to handle complex data conflicts and ensure higher data integrity.

- **Enhanced Security Features**:  
  Incorporate advanced encryption methods to further secure sensitive delivery data both at rest and in transit.

## Risks and Mitigations

1. **Data Security**:  
   Once a package is delivered and its data is uploaded, the app deletes its data in the database to prevent any security risks.

2. **Data Inconsistency**:  
   There is a possibility that some key information might be modified during delivery, which could impact the accuracy of package deliveries. Implementing a lightweight data synchronization API helps mitigate this risk by ensuring data consistency.

## Contributing

Contributions are welcome! If you have suggestions or would like to contribute to the project, please fork the repository and submit a pull request.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
