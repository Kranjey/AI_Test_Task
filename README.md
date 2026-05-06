# QA Automation AI-Track Test Assignment

## 🚀 Быстрый старт

### Требования
- Java 17+
- Maven 3.8+
- Chrome browser + ChromeDriver (или WebDriverManager)

### Запуск тестов
```bash
# Локально
mvn test

# Параллельно (4 потока)
mvn test -Dparallel.tests=4

# С кастомной конфигурацией
BASE_URL=https://staging.example.com mvn test