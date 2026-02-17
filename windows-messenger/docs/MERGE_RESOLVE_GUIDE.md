# Merge/Resolve guide for windows-messenger

Если конфликт при pull/merge в VS Code показывает:
- **Accept Current Change**
- **Accept Incoming Change**
- **Accept Both Changes**

используйте такие правила:

## 1) Для network/auth логики (обычно выбирать Current)

Обычно оставляйте **Accept Current Change** для файлов:
- `src/api.ts`
- `src/config.ts`
- `electron/main.cjs`
- `electron/preload.cjs`

Причина: здесь лежат IPC bridge + fallback на `api/windows_app` + нормализация payload.

## 2) Для UI-стилей

Для `src/App.tsx`, `src/styles.css` если обе стороны полезны —
можно выбрать **Accept Both Changes**, затем вручную убрать дубли JSX/CSS.

## 3) Никогда не коммитить конфликтные маркеры

Перед commit обязательно проверьте, что нет строк:
- `<<<<<<<`
- `=======`
- `>>>>>>>`

Команда проверки:

```bash
rg "^(<<<<<<<|=======|>>>>>>>)" windows-messenger
```

Если вывод пустой — маркеров нет.

## 4) Быстрый безопасный workflow

```bash
git fetch origin
git merge origin/<branch>
# resolve conflicts
git add .
git commit
```

Если сомневаетесь, лучше временно сохранить backup текущего файла и выбрать Current,
потом вручную точечно перенести нужные куски Incoming.
