LUMEN VISUALS — Fabric 1.21.4 (клиентский мод, Java 21)

КАК ОТКРЫТЬ МЕНЮ
  RShift  ИЛИ  в чате напиши  .gui   (на телефоне без клавиатуры — второй способ)
  При первом входе в мир в чате появится «[Lumen] Мод загружен…» — значит мод работает.
  Если красивая отрисовка ломается, мод не падает: рисует «Lumen (safe)» и пишет ошибку в чат и в лог.

ВКЛАДКИ МЕНЮ: Visuals | ESP | Target | World | View | Cosmetics | Theme

ENTITY ESP    — «заливка» сквозь стены: у игроков/зомби/скелетов собирается из частей модели (голова, торс,
                руки и ноги шагают), у остальных — из хитбокса. Цвета игроков/врагов/мирных, прозрачность
                заливки, толщина линий, свечение рёбер. Режим box — старые 2D-рамки с HP.
TARGET        — Target ESP (кольцо/бокс) и Target HUD (liquid glass).
WORLD         — пресеты Newyear / Sunset / Aurora / Sakura / Neon, насыщенность (ниже 100% — обесцвечивание),
                затемнение, туман, время суток, СВОИ частицы (плавные звёзды, круглый снег, дым),
                небо-плазма (aurora, nebula, solar, toxic).
VIEW MODEL    — позиция X/Y/Z, размер, поворот предмета в руке, убрать анимацию подъёма,
                Swing animation: vanilla, smooth, slow, snap, stab, slash, spin, bounce, none + скорость.
COSMETICS     — нимб, звёзды-аура, звёздный шлейф: размер, скорость, цвет (или цвет темы).
THEME         — цвет темы (палитра + оттенок/насыщенность/яркость), прозрачность стекла.

ЧАТ (с точкой или /lumen ...):
  .gui                      открыть меню
  .lumen help
  .theme HEX | .theme hsv H S V | .theme reset
  .thud / .tesp ...         (см. .lumen help)
  .world preset newyear|sunset|aurora|sakura|neon|off
  .world sat N | dark N | snow N | stars N | smoke ..|plasma ..|fog ..|time ..

Настройки: config/lumenvisuals.json

СБОРКА: залить zip в репозиторий GitHub (workflow build.yml уже есть) -> Actions -> Artifacts.
