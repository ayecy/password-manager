Password Manager 1.0:
    - Stable working version with master-key access only
    - No Edit/Delete account data icons
    - There is no option to change the master-key, there is only a corresponding button

Password Manager 1.1:
    - New Edit/Delete buttons and colors
    - New hover effects for Edit/Delete buttons in index.css

Password Manager 1.2:
    - Removed unused dependencies in backend/target/classes/application.properties
    - Updated database location to match production environment.
    - Updated repository methods to catch IOException and rethrow as RuntimeException in 
      backend/src/main/java/com/example/passwordmanager/repository/JsonPasswordRepository.java
    - Added logging for database path resolution and retrieval errors in
      backend/src/main/java/com/example/passwordmanager/repository/JsonPasswordRepository.java

Password Manager 2.0:
    - Full web-app redesign