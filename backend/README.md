# ProjectPulse Backend

Spring Boot backend for the ProjectPulse Phase 1 MVP.

## Requirements

- Java 21
- Maven 3.9+

## PowerShell Commands

Run tests:

```powershell
cd backend
mvn test
```

Build:

```powershell
cd backend
mvn clean package
```

Run locally:

```powershell
cd backend
mvn spring-boot:run
```

Health check:

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/health
```

Scan immediate child folders:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/scan `
  -ContentType "application/json" `
  -Body '{"rootPath":"C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects"}'
```
