#set document(
  title: "Работа № 1. Разработка защищенного REST API с интеграцией в CI/CD",
  author: "Джохадзе Анна Бекаевна",
  date: datetime(year: 2026, month: 10, day: 29),
)
#set text(font: "Times New Roman", size: 12pt, lang: "ru")
#set page(
  paper: "a4",
  margin: (left: 25mm, right: 20mm, top: 20mm, bottom: 20mm),
  numbering: "1",
  number-align: center,
)
#set par(justify: true, leading: 0.55em, spacing: 0.65em)
#set heading(numbering: "1.1.")
#show heading.where(level: 1): set text(size: 16pt)
#show heading.where(level: 2): set text(size: 14pt)
#show heading.where(level: 3): set text(size: 12pt)
#show heading.where(level: 4): set text(size: 12pt)
#show raw: set text(font: "Courier New", size: 10pt)
#show raw.where(block: true): set block(breakable: false, above: 7pt, below: 7pt)
#set figure(gap: 7pt)
#show figure.caption: set text(size: 11pt)
#show link: set text(fill: black)

#let pipeline-url = read("pipeline.txt").trim()

#context {
  set page(numbering: none)
  set par(justify: false, leading: 0.6em)
  align(center)[
    Федеральное государственное автономное образовательное учреждение\
    высшего образования\
    «Национальный исследовательский университет ИТМО»
  ]
  v(42mm)
  align(center)[
    #text(size: 14pt, weight: "bold")[Работа № 1]

    #text(size: 14pt, weight: "bold")[«Разработка защищенного REST API с интеграцией в CI/CD»]

    по дисциплине «Информационная безопасность»
  ]
  v(43mm)
  align(right)[
    Джохадзе Анна Бекаевна\
    P3410
  ]
  v(1fr)
  align(center)[2026]
}
#pagebreak()

= Ссылка на публичный репозиторий
#link("https://github.com/bakuani/security_lab1")

= Текст README.md
#include "readme.typ"

= Ссылка на последний успешный запуск pipeline
#link(pipeline-url)

