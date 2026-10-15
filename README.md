# Focus

**Focus** is an Android app that helps you study and use your phone on purpose: per-subject checklists with
generous timers, a treat bank you earn by finishing tasks, a soft gate in front of distracting apps, Shorts/Reels
counting, locked "not-free" time blocks, rich notes, a PDF reader, an embedded treat player, streaks and badges.
Everything stays on your phone.

## Features

* Subjects with task lists, an extendable and pausable timer, alarm with sound, and spoken over-time reminders
* Treat bank: each subject's reward is split across its tasks; spend it at the gate, on Shorts, or on the in-app player
* Soft gate for apps you choose, plus locked blocks where only allowed apps work
* Shorts/Reels detection with swipe counting (optional root mode for exact counting)
* Notes: text and checklists, rich text (H1-H3, bold, italic, underline, strikethrough, colours), labels, archive, trash, backup
* PDF library and reader, in-app media player
* `.foc` / `.fcs` files: video/audio, PDF, or JSON (today's work, config, notes, language packs) detected from the file header
* Streaks, XP, badges and a hidden Hall of Shame
* Arabic (right-to-left) and importable language packs

## License

Focus is free software licensed under the **GNU Affero General Public License v3.0 only**
(`AGPL-3.0-only`). See [`LICENSE`](LICENSE). Every source file carries the header in
[`docs/LICENSE_HEADER.txt`](docs/LICENSE_HEADER.txt).

## Credits

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). In short: the feature set of the notes module follows
[NotallyX](https://github.com/Crustack/NotallyX) (Crustack, forked from [Notally](https://github.com/OmGodse/Notally)
by OmGodse; GPL-3.0), and PDFs can be opened in
[Episteme Reader](https://github.com/Aryan-Raj3112/episteme) (Aryan-Raj3112; AGPL-3.0-only), a separate app that is not
bundled. The code in this repository is an independent implementation.

## Build

```bash
gradle assembleDebug          # Android SDK required; local.properties holds sdk.dir
```

In Termux set `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2` in `~/.gradle/gradle.properties`.

---

<div dir="rtl" lang="ar">

# فوكس (Focus)

**فوكس** تطبيق أندرويد يساعدك على الدراسة واستخدام هاتفك بقصد: قوائم مهام لكل مادة مع مؤقتات سخية،
ورصيد مكافآت تكسبه بإنجاز المهام، وبوابة ناعمة أمام التطبيقات المشتِّتة، وعدّ مقاطع شورتس/ريلز،
وفترات «غير متاح» مقفلة، وملاحظات غنية، وقارئ PDF، ومشغّل وسائط مدمج، وسلاسل أيام وأوسمة. كل شيء يبقى على هاتفك.

## المزايا

* مواد بقوائم مهام ومؤقت قابل للتمديد والإيقاف المؤقت، ومنبّه بصوت، وتذكيرات منطوقة عند تجاوز الوقت
* رصيد المكافآت: مكافأة كل مادة موزعة على مهامها، وتصرفها عند البوابة أو في شورتس أو في المشغّل المدمج
* بوابة ناعمة للتطبيقات التي تختارها، وفترات مقفلة لا تعمل فيها إلا التطبيقات المسموحة
* اكتشاف شورتس/ريلز وعدّ المقاطع (مع وضع اختياري بصلاحيات الروت للعدّ الدقيق)
* ملاحظات: نص وقوائم مهام، ونص منسّق (عناوين H1-H3 وعريض ومائل وتسطير وشطب وألوان)، وتصنيفات وأرشيف ومهملات ونسخ احتياطي
* مكتبة PDF وقارئ، ومشغّل وسائط مدمج
* ملفات `.foc` / `.fcs`: فيديو/صوت أو PDF أو JSON (عمل اليوم، الإعدادات، الملاحظات، حزم اللغات) تُعرَف من ترويسة الملف
* سلاسل أيام ونقاط خبرة وأوسمة و«قاعة العار» المخفية
* العربية (من اليمين إلى اليسار) وحزم لغات قابلة للاستيراد

## الترخيص

فوكس برنامج حر مرخّص بموجب **رخصة جنو العمومية الأفيرو الإصدار 3 فقط** (`AGPL-3.0-only`).
انظر ملف [`LICENSE`](LICENSE). يحمل كل ملف مصدري الترويسة الموجودة في
[`docs/LICENSE_HEADER.txt`](docs/LICENSE_HEADER.txt).

## الشكر والإسناد

انظر [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). باختصار: مجموعة مزايا وحدة الملاحظات تتبع مشروع
[NotallyX](https://github.com/Crustack/NotallyX) (للمطوّر Crustack، مشتق من
[Notally](https://github.com/OmGodse/Notally) للمطوّر OmGodse؛ رخصة GPL-3.0)، ويمكن فتح ملفات PDF في
[Episteme Reader](https://github.com/Aryan-Raj3112/episteme) (للمطوّر Aryan-Raj3112؛ رخصة AGPL-3.0-only)، وهو تطبيق منفصل غير مضمَّن.
الشيفرة في هذا المستودع تنفيذ مستقل.

</div>
