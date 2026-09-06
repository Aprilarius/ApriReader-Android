# Описание приложения ApriReader для Google Play и RuStore

Единственный источник текста карточки — раньше существовал ещё черновик в
`play/store-listing-ru.md`, он устарел (не упоминал аудиокниги) и был удалён
2026-09-05; всё нужное из него перенесено сюда.

---

## 🇷🇺 Русская версия (для RuStore и Google Play)

### 📌 Название приложения (до 30 символов)
`ApriReader: Книги и Аудиокниги`

*(Альтернатива для поиска: `Apri — Читалка FB2, EPUB, PDF`)*

---

### ⚡ Краткое описание (до 80 символов)
`Удобная читалка и плеер аудиокниг: озвучка TTS, скорочтение, FB2, EPUB, PDF, M4B`

---

### 📖 Полное описание (до 4000 символов)

```
ApriReader — это современный, быстрый и красивый ридер для тех, кто по-настоящему любит читать и слушать книги. Никакой рекламы, никаких навязчивых подписок и скрытых трекеров: только вы и ваша любимая литература.

Открывая книгу, приложение забирает палитру с её обложки и перекрашивает экран чтения под неё — контраст текста при этом проверяется автоматически, поэтому яркая обложка не превращается в нечитаемую страницу. Не нравится подобранный цвет — закрепите свой: он останется за книгой и не будет перезаписан.

ЧТО УМЕЕТ

• Форматы: EPUB, FB2 (включая .fb2.zip), PDF, TXT, HTML, комиксы CBZ и CBR
• Аудиокниги: M4B с главами, MP3, M4A, AAC, FLAC, OGG, OPUS
• Пять стилей оформления на выбор: Liquid Glass, Glassmorphism, Solid Clean, Neumorphism, Wood Library — не просто смена акцента, а разная форма элементов и поверхностей
• Тёмная и светлая темы, следование системной, глубокий чёрный для OLED
• Стили страницы при чтении: бумага, сепия, графит, чёрный
• 20 гарнитур (Literata, EB Garamond, Atkinson Hyperlegible, Lexend, Inter и другие) или подключение своего TTF/OTF; точная настройка кегля, интерлиньяжа, полей, выключки
• Сохранение настроек типографики в именованные пресеты
• Оглавление, поиск по тексту книги, закладки, выделения и экспорт выписок
• Статистика чтения: время, серии дней, достижения
• Виджет «текущая книга» на домашнем экране

АУДИОКНИГИ

• Воспроизведение M4B с главами и плейлистов MP3
• Память прогресса с точностью до секунды
• Плавная смена скорости воспроизведения (0.5x–3.0x) без искажения тембра
• Таймер сна: книга остановится сама
• Управление с экрана блокировки и из уведомления

ДЛЯ ТЕХ, КОМУ ТРУДНО УДЕРЖАТЬ ВНИМАНИЕ

• Бионическое чтение — начало каждого слова выделяется, глазу проще держаться строки
• RSVP — слова показываются по одному в фиксированной точке экрана, от 100 до 900 слов в минуту
• Чтение вслух системным голосом с подсветкой читаемого абзаца
• Шрифты Atkinson Hyperlegible и Lexend, созданные для облегчения чтения

УМНАЯ БИБЛИОТЕКА

• Автоматический поиск обложек и описаний книг онлайн (FantLab, Open Library, Google Books, Википедия) — по вашему запросу для конкретной книги
• Сортировка по авторам, сериям, жанрам, дате добавления и прогрессу

ПРИВАТНОСТЬ БЕЗ ЗВЁЗДОЧЕК

• Нет рекламы, встроенных покупок и подписок. Всё бесплатно
• Нет аналитики, трекеров и сторонних SDK
• Библиотека, прогресс чтения и статистика не покидают устройство
• Доступ в интернет нужен ровно для одного действия: подтянуть обложку и описание книги, когда вы сами нажмёте кнопку. Приложение спросит подтверждение и покажет, что именно будет отправлено. Остальные разрешения — только для воспроизведения аудиокниг (уведомление с управлением плеером и его работа при выключенном экране)
• Облачной синхронизации нет — это осознанное решение, а не недоработка

КАК УСТРОЕН ДОСТУП К ФАЙЛАМ

Вы выбираете папку с книгами через системный диалог Android. Приложение запоминает доступ и находит внутри всё поддерживаемое, включая вложенные папки. Если доступ к папке потерян — книги остаются в библиотеке вместе с прогрессом и закладками и возвращаются, как только папка будет подключена снова.

Требуется Android 8.0 или новее.
```
*≈2500 символов*

---

### 🆕 Что нового в версии 2.0.0:
- 📂 Исправлен выбор файлов комиксов (CBZ/CBR) в системном диалоге — файлы больше не становятся недоступны для выбора.
- 🖼️ Исправлено повреждение отдельных страниц комиксов при быстром листании.
- 📖 Исправлено постраничное листание и переключение между листанием и прокруткой — позиция в книге больше не теряется.
- 🎨 Стили оформления Glassmorphism и Neumorphism переработаны и стали по-настоящему различимыми.
- 🎧 Аудиоплеер надёжнее переживает закрытие приложения из недавних.
- 🔒 Повышена устойчивость к повреждённым файлам книг и комиксов.
- 🌍 Завершены переводы интерфейса на немецкий, азербайджанский и итальянский.

---

### 🔑 Ключевые слова (для поиска / ASO):
`читалка, ридер, книги, аудиокниги, fb2, epub, pdf, m4b, комиксы, cbz, cbr, офлайн, без рекламы, озвучка текста, tts, скорочтение, bionic reading, rsvp, сдвг, тёмная тема, читалка книг, aprireader`

### Категория и теги

- Категория: **Книги и справочники**
- Теги: чтение, электронные книги, аудиокниги, офлайн
- Тип: приложение (не игра)

### Контактные данные (заполняются владельцем)

- Email разработчика: _обязателен, виден публично_
- Сайт: _необязателен_
- Политика конфиденциальности: https://aprilarius.github.io/aprireader-android-privacy/

---

## 🇬🇧 English Version (for Google Play Global)

### 📌 App Title (up to 30 chars)
`ApriReader: Books & Audiobooks`

---

### ⚡ Short Description (up to 80 chars)
`Modern book reader & audiobook player: TTS text-to-speech, EPUB, FB2, PDF, M4B`

---

### 📖 Full Description (up to 4000 chars)

```
ApriReader is a fast, clean, and elegant reading app and audiobook player designed for true book lovers. No ads, no tracking, no subscriptions — just an exceptional reading experience.

Open a book and the reading screen takes its accent color from the cover — contrast is checked automatically, so a bright cover never turns into unreadable text. Don't like the picked color? Pin your own; it stays with the book and is never overwritten.

WHAT IT DOES

- Formats: EPUB, FB2 (including .fb2.zip), PDF, TXT, HTML, CBZ and CBR comics
- Audiobooks: M4B with chapters, MP3, M4A, AAC, FLAC, OGG, OPUS
- Five design styles to choose from: Liquid Glass, Glassmorphism, Solid Clean, Neumorphism, Wood Library — not just a different accent, but genuinely different shapes and surfaces
- Dark and light themes, follow system, deep black for OLED
- Page styles while reading: paper, sepia, graphite, black
- 20 typefaces (Literata, EB Garamond, Atkinson Hyperlegible, Lexend, Inter, and more) or import your own TTF/OTF; precise control over size, line height, margins, justification
- Save typography settings as named presets
- Table of contents, in-book search, bookmarks, highlights with Markdown export
- Reading stats: time, streaks, achievements
- "Currently reading" home-screen widget

AUDIOBOOKS

- Plays M4B with chapters and MP3 playlists
- Progress remembered to the second
- Smooth speed control (0.5x-3.0x) without pitch distortion
- Sleep timer
- Lock-screen and notification playback controls

FOR READERS WHO FIND IT HARD TO STAY FOCUSED

- Bionic Reading — the start of each word is bolded, making it easier for your eye to hold the line
- RSVP — words flash one at a time at a fixed point, 100 to 900 words per minute
- Read-aloud with the system voice, current paragraph highlighted
- Atkinson Hyperlegible and Lexend fonts, designed to make reading easier

SMART LIBRARY

- Optional cover and metadata lookup (FantLab, Open Library, Google Books, Wikipedia) — only when you ask for a specific book
- Sort by author, series, genre, date added, or progress

PRIVACY WITHOUT ASTERISKS

- No ads, no in-app purchases, no subscriptions. Completely free
- No analytics, no trackers, no third-party SDKs
- Your library, reading progress, and stats never leave the device
- Internet access is used for exactly one action: fetching a book's cover and description, only when you tap the button yourself. The app asks for confirmation and shows exactly what will be sent. The remaining permissions are only for audiobook playback (notification controls, playback while the screen is off)
- No cloud sync — a deliberate limitation, not an oversight

HOW FILE ACCESS WORKS

You pick your books folder through Android's own system picker. The app remembers access and finds everything supported inside, including subfolders. If access to a folder is revoked, your books stay in the library with their progress and bookmarks intact, and come back as soon as the folder is reconnected.

Requires Android 8.0 or newer.
```

---

### 🆕 What's New in Version 2.0.0:
- 📂 Fixed comic file (CBZ/CBR) picking in the system dialog — files no longer show up greyed out.
- 🖼️ Fixed occasional comic page corruption when flipping through pages quickly.
- 📖 Fixed paged reading and the scroll/paging toggle — your position no longer resets.
- 🎨 Glassmorphism and Neumorphism themes reworked to actually look distinct.
- 🎧 More reliable audiobook playback when closing the app from recents.
- 🔒 Hardened against corrupted book and comic files.
- 🌍 Completed German, Azerbaijani, and Italian translations.

### 🔑 Keywords (ASO):
`reader, ebook, books, audiobooks, fb2, epub, pdf, m4b, comics, cbz, cbr, offline, no ads, text to speech, tts, speed reading, bionic reading, rsvp, dark theme, aprireader`

---

## Чего в карточке пока нет

**Скриншоты.** Play требует минимум 2 (рекомендуется 4–8) для телефона,
формат PNG/JPEG, минимальная сторона 320 px, максимальная 3840 px. Снять их без
устройства невозможно — это единственный обязательный материал, который придётся
сделать вручную. Рекомендуемый набор:

1. Полка с обложками и карточкой «Продолжить чтение»
2. Экран чтения со включённой темизацией по обложке
3. Тот же разворот с включённой бионикой (виден контраст с обычным текстом)
4. Режим RSVP
5. Лист оформления с настройками типографики или один из пяти стилей дизайна
6. Экран статистики с достижениями
7. Полноэкранный плеер аудиокниги

**Промо-видео** — необязательно.
