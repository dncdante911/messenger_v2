# WorldMates Messenger — Icon Generation Prompts

## Для чего использовать
Midjourney, Leonardo AI, DALL-E 3, Adobe Firefly, Stable Diffusion

---

## 1. APP ICON (основная иконка)

### Midjourney / Leonardo AI prompt:
```
Cute cartoon Shih Tzu puppy with brown and white fur, wearing silver gaming headphones 
with a small blue microphone, sitting inside a white speech bubble shape, 
soft teal-to-mint gradient background (#4ECDC4 to #95E1A3), rounded square app icon 
format, flat design with subtle drop shadow, friendly and modern messenger app icon, 
collar with small "W" letter charm, big expressive brown eyes, happy open mouth smile, 
fluffy fur texture, 2D cartoon style, clean white outline around the dog, 
professional mobile app icon, iOS/Android style, 1024x1024px --ar 1:1 --style raw --q 2
```

### Negative prompt (для Stable Diffusion):
```
realistic, photo, 3d render, dark, scary, complex background, text, watermark, 
multiple dogs, blurry, low quality
```

---

## 2. NOTIFICATION ICON (монохромная, 24x24dp)

```
Simple minimalist outline icon of a dog head with speech bubble, 
single color white, notification icon style, Android material design, 
very simple clean lines, no fill, stroke only, transparent background
```

---

## 3. SPLASH / LOADING STATES (для Lottie)

Эти анимации ищи на **lottiefiles.com** по запросам:
- `cute dog animation` → для загрузки
- `dog waving` → для splash screen  
- `sad dog` → для экрана ошибки
- `dog typing` → для индикатора "пишет..."

После скачивания — положить в `app/src/main/res/raw/`:
- `splash_mascot.json`
- `loading_mascot.json`
- `error_mascot.json`

---

## 4. Размеры иконок для Android

После генерации иконки нарежь её через **Android Asset Studio**:
https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html

Или через **Android Studio**: правая кнопка на `res` → New → Image Asset

| Папка | Размер |
|---|---|
| mipmap-mdpi | 48x48 |
| mipmap-hdpi | 72x72 |
| mipmap-xhdpi | 96x96 |
| mipmap-xxhdpi | 144x144 |
| mipmap-xxxhdpi | 192x192 |
| Play Store | 512x512 |

---

## 5. ADAPTIVE ICON структура

```
ic_launcher.xml          ← обёртка (уже создана в проекте)
ic_launcher_foreground   ← сам маскот (PNG 432x432, прозрачный фон)
ic_launcher_background   ← градиент (уже создан: teal→green)
ic_launcher_monochrome   ← белый силуэт (для Android 13 themed icons)
```
