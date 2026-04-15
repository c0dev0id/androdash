# Input and Navigation Model

Supported Input Sources
- Touch: android default behavior
- Mouse: android default behavior
- Keyboard: Custom Mapping
- Remote: Custom Mapping

## Custom Mapping 

### Keyboard

Arrow Left -> Move Focus Left
Arrow Right -> Move Focus Right
Arrow Down -> Move Focus Down
Arrow Up -> Move Focus Up
Return -> Activate Focused Element
Esc -> Set the focus on the first LetterBar Button from the left and unselect all LetterBar Buttons
Backspace -> Set the Focus on the last selected LetterBar Button and unselect it

Only react on key_down events with repeat 0.

### Remote

Listen to the broadcast intents on `com.thorkracing.wireddevices.keypress`.

KEYCODE_ENTER -> Activate Focused Element
KEYCODE_ESC -> Set the focus on the first LetterBar Button from the left and unselect all LetterBar Buttons

Only react on key_down events with repeat 0.

Use the Joystick Input on the `joy` extra intent to move the focus:

The input signal format is: `[U|D<mag>][L|R<mag>]` where <mag> is 0-5.
The input signal `Y0X0` is neutral.

**L5 -> Move Focus Left
**R5 -> Move Focus Right
D5** -> Move Focus Down
U5** -> Move Focus Up

Only move the focus once, then require neutral (`Y0X0`) to re-arm the detection. The U*L* Signal is handled as key_down, and the Y0X0 neutral signal acts as key_up.

