import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.*;
import java.io.*;

public class ConsolePasswordManager {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final String FILE_NAME = "passwords.txt";
    private static final Scanner scanner = new Scanner(System.in);

    private static Map<String, String> passwordStorage = new HashMap<>();

    private static String masterKey;

    public static void main(String[] args) {
        System.out.println("=== Консольный менеджер паролей ===");

        System.out.print("Введите мастер-ключ (16 символов): ");
        masterKey = scanner.nextLine();

        loadPasswordsFromFile();

        while (true) {
            System.out.println("\nМеню:");
            System.out.println("1. Добавить новый пароль");
            System.out.println("2. Посмотреть сохранённые пароли");
            System.out.println("3. Изменить пароль");
            System.out.println("4. Удалить пароль");
            System.out.println("5. Выйти");
            System.out.print("Ваш выбор: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1" -> addPassword();
                case "2" -> showPasswords();
                case "3" -> editPassword();
                case "4" -> deletePassword();
                case "5" -> {
                    System.out.println("Выход...");
                    savePasswordsToFile();
                    return;
                }
                default -> System.out.println("Неверный ввод, попробуйте снова!");
            }
        }
    }


    private static void addPassword() {
        System.out.print("Введите название сервиса: ");
        String service = scanner.nextLine();

        if (passwordStorage.containsKey(service)) {
            System.out.println("Такой сервис уже существует! Используйте пункт 'Изменить пароль'.");
            return;
        }

        System.out.print("Введите пароль: ");
        String password = scanner.nextLine();

        try {
            String encrypted = AESGCMUtil.encrypt(password, masterKey);
            passwordStorage.put(service, encrypted);
            savePasswordsToFile();
            System.out.println("Пароль сохранён.");
        } catch (Exception e) {
            System.out.println("Ошибка при шифровании: " + e.getMessage());
        }
    }


    private static void showPasswords() {
        if (passwordStorage.isEmpty()) {
            System.out.println("Нет сохранённых паролей.");
            return;
        }

        System.out.println("\n=== Ваши пароли ===");
        for (Map.Entry<String, String> entry : passwordStorage.entrySet()) {
            try {
                String decrypted = AESGCMUtil.decrypt(entry.getValue(), masterKey);
                System.out.println(entry.getKey() + ": " + decrypted);
            } catch (Exception e) {
                System.out.println(entry.getKey() + ": [Ошибка при расшифровке]");
            }
        }
    }
    private static void editPassword() {
        System.out.print("Введите название сервиса, пароль для которого хотите изменить: ");
        String service = scanner.nextLine();

        if (!passwordStorage.containsKey(service)) {
            System.out.println("Такой сервис не найден!");
            return;
        }

        System.out.print("Введите новый пароль: ");
        String newPassword = scanner.nextLine();

        try {
            String encrypted = AESGCMUtil.encrypt(newPassword, masterKey);
            passwordStorage.put(service, encrypted);
            savePasswordsToFile();
            System.out.println("Пароль успешно обновлён!");
        } catch (Exception e) {
            System.out.println("Ошибка при обновлении пароля: " + e.getMessage());
        }
    }
    private static void deletePassword() {
        System.out.print("Введите название сервиса, который хотите удалить: ");
        String service = scanner.nextLine();

        if (passwordStorage.remove(service) != null) {
            savePasswordsToFile();
            System.out.println("Запись удалена!");
        } else {
            System.out.println("Такого сервиса нет!");
        }
    }

    private static void loadPasswordsFromFile() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 2) {
                    passwordStorage.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка при чтении файла: " + e.getMessage());
        }
    }

    private static void savePasswordsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME))) {
            for (Map.Entry<String, String> entry : passwordStorage.entrySet()) {
                writer.write(entry.getKey() + "|" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Ошибка при записи файла: " + e.getMessage());
        }
    }
}
