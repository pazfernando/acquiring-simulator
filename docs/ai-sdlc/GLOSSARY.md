# Acquiring Simulator — product glossary

Product-specific user-facing terminology for the acquiring simulator, relocated here during the product-context split. Generic terms (AWS Console, terminal, local service test) remain in the shared AI-SDLC glossary. These labels are in Spanish for conversations with Fernando; they clarify references and do not rename code or change approved scope.

| Preferred term | Use it for | Do not confuse it with |
| --- | --- | --- |
| **Pantalla web de la demo** | The whole demo interface that the presenter opens in a browser. In the acquiring simulator, it covers signing in, submitting a test purchase, viewing the result and checking previous purchases. | The purchase form alone, the AWS Console or a terminal. Avoid bare “consola” or “formulario web” for the whole interface. |
| **Formulario de compra de prueba** | The part of the demo interface where the presenter enters and submits a test purchase. | The entire demo interface, its result view or its purchase history. |
| **Prueba de la demo desde el navegador** | A check of the presenter’s user journey through the demo interface. State which steps actually ran. | A service test that bypasses the interface. |

## Usage

- Use these terms consistently across conversations, progress updates and handoffs; map generic product wording to them when explaining.
- Name the whole interface and its parts distinctly. Prefer the clearest words over internal labels.
- Keep product-specific definitions here; keep generic team vocabulary in the shared AI-SDLC glossary so it is not duplicated.
