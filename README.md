# MyOwnBank Terminal

Платёжный терминал для приёма оплаты картами с поддержкой NFC, Face ID и оплаты по камере.

## Возможности

- 💳 **NFC оплата** - приложите карту к задней части телефона
- 📸 **Оплата по камере** - закройте фронтальную камеру картой для оплаты
- 👤 **Face ID** - оплата по распознаванию лица
- 🔥 **Firebase интеграция** - синхронизация с системой управления в реальном времени
- 📊 **История транзакций** - все платежи сохраняются
- ⚙️ **Настройка шанса неудачи** - для тестирования отклонённых платежей
- 🔄 **Автоматический повтор** - при неудачной оплате терминал предлагает повторить попытку

## Установка

1. Скачайте APK из [Releases](https://github.com/heyyaah/MyOwnBankTerminal/releases)
2. Установите на Android устройство (минимум Android 8.0)
3. Настройте Firebase Realtime Database
4. Укажите ID терминала в системе управления

## Требования

- Android 8.0 (API 26) или выше
- NFC (опционально, для оплаты картой)
- Фронтальная камера (для Face ID и оплаты по камере)
- Интернет соединение

## Демо

Веб-система управления терминалами доступна по адресу: https://paymentterminal-55866.web.app

Через неё можно:
- Управлять терминалами удалённо
- Инициировать платежи
- Просматривать историю транзакций
- Настраивать параметры терминала

## Настройка Firebase

1. Создайте проект в [Firebase Console](https://console.firebase.google.com/)
2. Добавьте Android приложение с package name `com.myownbank.terminal`
3. Скачайте `google-services.json` и поместите в `app/`
4. Настройте Realtime Database с правилами:

```json
{
  "rules": {
    "terminals": {
      ".read": true,
      ".write": true
    }
  }
}
```

## Сборка

```bash
# Debug версия
./gradlew assembleDebug

# Release версия (требуется keystore)
./gradlew assembleRelease
```

## Интеграция

Терминал работает через Firebase Realtime Database. Структура данных:

```
terminals/
  {terminalId}/
    command: "start" | "idle"
    price: 10000  // цена в копейках
    status: "idle" | "success" | "failed" | "cancel"
    failureChance: 10  // шанс неудачи 0-100
    failMessage: "Недостаточно средств"
    paymentTimeout: 60  // таймаут в секундах
    disabledPayment: "nfc" | "face" | ""
    history/
      {transactionId}/
        transactionId: "ABC123"
        price: 10000
        status: "success"
        method: "nfc" | "face" | "card"
        timestamp: 1714000000000
```

## Лицензия

MIT License - см. [LICENSE](LICENSE)

## Автор

MyOwnBank Terminal © 2026
