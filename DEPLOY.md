# 🚀 Руководство по развертыванию (CI/CD & Production Deployment)

В проекте настроен автоматический конвейер непрерывной интеграции и доставки (**GitHub Actions CI/CD**).

При каждом `git push` в ветку `master`:
1. **CI (Тестирование)**: GitHub Actions запускает виртуальную машину с Java 21, компилирует проект и прогоняет все автоматические тесты.
2. **CD (Сборка Docker)**: собирается оптимизированный образ и автоматически загружается в реестр контейнеров **GitHub Container Registry (`ghcr.io/kabarbekn/yesimde-stocks:latest`)**.
3. **CD (Авто-деплой)**: если настроены SSH-секреты вашего сервера, GitHub сам подключается к VPS и обновляет контейнеры без остановки работы.

---

## 1. Настройка автоматического деплоя (GitHub Secrets)

Чтобы GitHub Actions мог подключаться к вашему серверу, перейдите в вашем репозитории на GitHub:
👉 **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.

Добавьте следующие секреты:

| Имя секрета | Описание | Пример |
|---|---|---|
| `SERVER_HOST` | IP-адрес или домен вашего сервера | `194.87.123.45` |
| `SERVER_USER` | Пользователь Linux для подключения | `root` или `deploy` |
| `SERVER_SSH_KEY` | Приватный SSH-ключ (содержимое `id_ed25519` или `id_rsa`) | `-----BEGIN OPENSSH PRIVATE KEY----- ...` |
| `SERVER_PORT` | SSH-порт (необязательно, по умолчанию 22) | `22` |
| `SERVER_DEPLOY_PATH` | Папка с проектом на сервере | `/opt/yesimde-stocks` |

> [!NOTE]
> Если секрет `SERVER_HOST` не добавлен, CI-тесты и сборка Docker-образа в `ghcr.io` всё равно успешно выполняются, а шаг SSH-деплоя безопасно пропускается.

---

## 2. Быстрая подготовка сервера (VPS) за 5 минут

### Шаг 1: Установка Docker и Docker Compose на сервере
Подключитесь к вашему VPS по SSH и выполните (для Ubuntu / Debian):

```bash
# Обновление пакетов
sudo apt-get update && sudo apt-get upgrade -y

# Установка Docker и Docker Compose Plugin
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Проверка
docker --version
docker compose version
```

### Шаг 2: Создание рабочей директории и клонирование
```bash
# Создаем рабочую папку
sudo mkdir -p /opt/yesimde-stocks
sudo chown -R $USER:$USER /opt/yesimde-stocks
cd /opt/yesimde-stocks

# Клонируем репозиторий
git clone https://github.com/KabarbekN/yesimde-stocks.git .
```

### Шаг 3: Настройка переменных окружения (`.env`)
Создайте файл `.env` в папке `/opt/yesimde-stocks`:

```bash
cp .env.example .env
nano .env
```

Заполните ваши боевые параметры:
```env
# Пароль для базы данных PostgreSQL
DB_PASSWORD=your_strong_postgres_password_here

# Telegram Бот
TELEGRAM_BOT_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
TELEGRAM_BOT_USERNAME=KaseRadarBot
TELEGRAM_BOT_ENABLED=true

# Реестр Docker (образ, собранный через GitHub Actions)
DOCKER_IMAGE=ghcr.io/kabarbekn/yesimde-stocks:latest
```

---

## 3. Настройка SSH-доступа для GitHub Actions

На своем локальном компьютере (или на сервере) сгенерируйте отдельную пару SSH-ключей для деплоя:

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ./deploy_key -N ""
```

1. Содержимое открытого ключа `deploy_key.pub` добавьте на сервер в файл `~/.ssh/authorized_keys`:
   ```bash
   cat deploy_key.pub >> ~/.ssh/authorized_keys
   chmod 600 ~/.ssh/authorized_keys
   ```
2. Содержимое закрытого ключа `deploy_key` скопируйте в секрет `SERVER_SSH_KEY` на GitHub.

---

## 4. Запуск и полезные команды на сервере

### Запуск вручную (первый раз):
```bash
cd /opt/yesimde-stocks
docker compose up -d
```

### Просмотр логов:
```bash
# Логи приложения (Spring Boot + Telegram-бот)
docker compose logs -f app

# Логи базы данных PostgreSQL
docker compose logs -f postgres
```

### Проверка статуса контейнеров:
```bash
docker compose ps
```

### Перезапуск сервисов:
```bash
docker compose restart
```

### Остановка:
```bash
docker compose down
```

---

## 5. Как работает веб-интерфейс на сервере

После запуска сервиса веб-интерфейс KASE & AIX Radar будет доступен по адресу:
- `http://IP_ВАШЕГО_СЕРВЕРА:8080/`

При необходимости можно настроить обратный прокси (**Nginx** или **Caddy**) с бесплатным SSL-сертификатом от Let's Encrypt для работы по домену (HTTPS).
