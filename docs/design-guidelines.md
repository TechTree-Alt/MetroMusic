# Windows Phone design rules this app follows

The rules MetroMusic is checked against, and where each one is written down. Every source below is a
Microsoft document from the platform's own time; none of it is paraphrased from memory.

## Sources

| | Document | Where to find it |
|---|---|---|
| **SDK** | *Windows Phone 8 Developer Documentation* (offline help, September 2012) | `Windows Phone 8 Developer Documentation.chm` in the Windows Phone 8.0 SDK, archived at archive.org (`windows-phone-8.0-rtm-sdk`) |
| **Theme** | `ThemeResources.xaml`, dark and light | the same SDK, `Design` folder (`WPSDK_en.cab`) |
| **Toolkit** | Windows Phone Toolkit, `Generic.xaml` control templates | NuGet package `WPtoolkit` 4.2013.8.16, authored by Microsoft |
| **Guide** | *UI Design and Interaction Guide for Windows Phone 7*, v2.0, July 2010 | archived at archive.org (`ui-design-and-interaction-guide-for-windows-phone-7-v-2.0`) |
| **Cert** | *Technical certification requirements for Windows Phone* | in the SDK documentation |

## Application bar

- Along the bottom of the page; up to four icon buttons; an ellipsis that shows the captions and the
  menu. *(SDK: App bar for Windows Phone; Guide: Application bar)*
- On a panorama, use the mini size. *(SDK: "Use the mini size on pages where you want to maximize
  screen space, such as on panorama pages.")*
- Menu items for less frequent actions and for actions an icon cannot convey; at most five; 14–20
  characters; lowercase. *(SDK; Guide: Application bar menu)*
- Closed by tapping the dots, tapping outside it, Back, or choosing a button or item. *(Guide)*
- Opacity 0, 0.5 or 1 only; at 1 the page is resized rather than covered. *(SDK; Guide)*
- Colour: `PhoneChromeColor`, #1F1F1F dark / #DDDDDD light. *(Theme)*
- No button that navigates back. *(SDK: App bar icon buttons)*

## Panorama

- At most four sections. *(SDK: Panorama control features and best practices)*
- The panorama title should be the same as the app's Start tile, and should not be animated or
  change size. *(SDK; Guide: Panorama)*
- No pivot inside a panorama; vertical scrolling only within a section. *(Guide: Panorama, Pivot)*

## Pages and navigation

- A page title, when shown, does not scroll, and keeps the same space on every page. *(Guide: Page
  title)*
- Settings pages are headed `SETTINGS` over the panel's name, apply changes immediately without an
  OK, and anything that deletes data offers cancel. *(Guide: Application settings)*
- Back goes back, and closes an open menu or dialog first. On the first page it closes the app —
  *or lets the user confirm closing it*, which is what this app's two-press exit does. *(Cert
  5.2.4.1–5.2.4.3)*

## Text

- Capitalisation *(Guide: Text guidelines – capitalization)*:
  - lowercase: page titles, list titles, list group titles, buttons and commands, list items, search
    box hints, links inside a sentence;
  - Sentence caps: check box and radio labels, progress indicators, status, notification and
    explanatory text, toggle switches;
  - ALL CAPS: application titles, dates and times.
- Punctuation *(Guide: Text guidelines – punctuation)*: an ellipsis on progress text, never on a
  button or heading; end punctuation only on instructional text; no colon after a control's label.
- Button text is concise, usually a verb, at most two words. *(Guide: Push button)*
- Nothing smaller than 15 pt (20 px, `PhoneFontSizeNormal`); explanatory text is
  `PhoneTextSubtleStyle`. *(Guide: Fonts; Theme)*
- Sizes and families *(Theme)*: Normal 20, Medium 22.667, MediumLarge 25.333, Large 32,
  ExtraLarge 42.667, ExtraExtraLarge 72; page title `PhoneTextTitle1Style` (Semilight 72); panorama
  section header Semilight 66; group header Semilight 29.333 in the accent.

## Controls

- Toggle switch *(Toolkit)*: header in `PhoneSubtleBrush` at Normal (20), the state below it in
  Light at Large (32), the switch to the right; disabled is 30% opacity.
- Context menu items *(Toolkit)*: Semilight at Large (32); a disabled item at 50%.
- Message box *(Toolkit `CustomMessageBox`)*: on `PhoneChromeBrush`; two equal buttons, the positive
  one on the left. *(Guide: Push button)*
- Touch targets at least 9 mm, never under 7 mm, 2 mm apart; the visible part at least 60% of the
  target. *(Guide: Touch input)*
- Touch and hold opens a context menu. *(Guide)*

## Where the app still differs, and why

- The framework (MangoTile) sets some of the type scale and the dialogs; its overline is 13sp, under
  the 15 pt floor, and its message box sits on the page colour rather than the chrome colour. Those
  are changes for MangoTile, not for this app.
- The home panorama has more than four sections unless some are switched off — what it shows is the
  user's choice.
