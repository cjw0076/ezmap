# EZmap — On-Device AI Mobility Agent

서버 없이 안드로이드 앱 안에서 완전히 동작하는 **AI 에이전틱 내비게이션**.
사용자가 본인 API 키를 입력하면, Gemini가 앱 안에서 Function Calling으로 51개 도구를 직접 호출합니다.

> 핵심: **EZmap 서버가 없다. 앱이 곧 에이전트다.**
> 졸업작품(캡스톤) · 작동하는 프로토타입.

## 주요 특징

- **음성 에이전트** — "이지야" 웨이크워드 → 한 문장이 여러 도구를 엮어 종합
- **51개 Function Calling 도구** — 카카오·네이버·기상청·공공데이터 등 횡단 오케스트레이션
- **랜드마크 내비게이션** — "300m 앞 우회전" 대신 "전방 맥도날드 앞에서 우회전" (ICFICE 2026 논문 검증)
- **서버리스** — 오케스트레이션·기억·안전은 폰에서, LLM 추론은 사용자 키로 클라우드에 직접
- **비신뢰 LLM 하니스** — 강제 그라운딩·STT 재해석·안전 게이트로 LLM이 틀릴 때를 결정론적으로 가둠

## 빌드

```bash
# local.properties 에 본인 키 입력 (local.properties.example 참고)
JAVA_HOME="<Android Studio JBR>" ./gradlew assembleDebug
JAVA_HOME="<Android Studio JBR>" ./gradlew installDebug
```

`local.properties` 필수 키: `GEMINI_API_KEY`, `KAKAO_REST_KEY`, `KAKAO_NATIVE_APP_KEY`
(선택: 공공데이터·오피넷·네이버·ODsay·Porcupine·Spotify) — `local.properties.example` 참고.

## 기술 스택

Kotlin · Jetpack Compose · Gemini 2.5 Flash (Function Calling) · Kakao Map/Mobility ·
Naver Directions · Porcupine + Vosk (웨이크워드) · Room + SQLCipher · Hilt · Retrofit · MCP

## 패키지

`com.example.ez_capstone` — agent / api / navi / voice / ui / db / skill / safety / mcp ...

---

TeamEZ · 졸업작품. 소스는 학습/시연 목적의 클린 스냅샷입니다(키·시크릿 미포함).
