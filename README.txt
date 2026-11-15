LUMEN VISUALS 2.0.0 — Fabric 1.21.4 (клиентский мод, Java 21)

ЧТО ВКЛЮЧЕНО
  Меню Liquid Glass: RShift или команда .gui. Вкладки Visuals | ESP | Target | World | View | Cosmetics | Theme.
  Entity ESP: заливочная модель сквозь стены, раздельные цвета игроков/врагов/мирных, прозрачность заливки,
    толщина и свечение рёбер; режим box также доступен. Визуальный стиль ориентирован на приложенный зелёный референс.
  View Model: позиция X/Y/Z, масштаб, поворот предмета, отключение анимации подъёма.
  Swing Animation: vanilla, smooth, slow, snap, stab, slash, spin, bounce, none; скорость настраивается.
  World: насыщенность (оверлейная коррекция), затемнение, туман, время суток, пресеты Newyear/Sunset/Aurora/Sakura/Neon.
  Частицы: собственные текстуры гладких звёзд, круглого снега и дыма; количество, размер и скорость.
  Plasma Sky: aurora, nebula, solar, toxic; яркость и скорость.
  Cosmetics: нимб, звёздная аура, шлейф, размер, скорость и цвет.
  Theme: HSV, палитра и прозрачность стекла. Настройки сохраняются в config/lumenvisuals.json.

СБОРКА ЧЕРЕЗ GITHUB
  1. Создай пустой GitHub-репозиторий.
  2. Распакуй этот архив и загрузи ВСЁ его содержимое в корень репозитория (папки .github и src должны быть видны в корне).
  3. Открой Actions -> Build Lumen Visuals -> Run workflow (или сделай push).
  4. После успешной сборки скачай lumen-visuals-fabric-1.21.4 из Artifacts запуска workflow.

ЛОКАЛЬНАЯ СБОРКА
  Требуется JDK 21 и Gradle 8.12+: gradle --no-daemon clean build
  Готовый JAR: build/libs/lumen-visuals-2.0.0.jar

СОВМЕСТИМОСТЬ
  Minecraft 1.21.4, Fabric Loader >= 0.16.10, Fabric API, Java 21.

ПРИМЕЧАНИЯ
  Проект сохраняет собственную лицензию All Rights Reserved. Код стороннего мода не включался;
  View Model и Swing Animation реализованы в исходниках Lumen Visuals.
  GitHub Actions выполняет настоящую Gradle-сборку и публикует JAR как artifact.
