# Duke — the DirectDesk phone application

Duke is the DirectDesk application for Android telephones. Duke puts the
DirectDesk assistant on the telephone in Solomon's pocket: Duke talks with
Solomon by text and by voice, Duke runs a language model directly on the
telephone so the conversation happens on the device itself, Duke exchanges
messages with the DirectDesk crew through the postbox, and Duke shows the
crew's work as a living task list that Solomon can rearrange by hand. Duke
is the telephone face of DirectDesk — the same Duke who manages the crew
on the computer, speaking from the phone.

What Duke does
--------------

- Duke converses. Solomon types or speaks to Duke, and Duke answers with
  text that can also be spoken aloud. Duke shows the model's thinking
  while the answer forms, and Duke keeps the conversation on the
  telephone.
- Duke runs the model on the telephone. Duke's model is named in one
  configuration line, marked off by a banner so the line is easy to find:
  the model is Qwen3_4b_thinking_dynamic_wi4b32_afp32.litertlm. Duke
  either downloads the model from the address on that line or loads the
  model from the telephone's own storage. In Version 4 the download
  address on the configuration line is blank, so Duke loads the model
  from the telephone's storage; filling that one line in a later version
  turns the download on without touching anything else in Duke.
- Duke talks to the crew through the postbox. Duke sends and receives
  messages through the Apps Script postbox, addressed to Duke and to
  Kevin — Operator, so a question asked on the telephone reaches the
  crew on the computer and the answer travels back the same road.
- Duke shows the work as tasks. The Tasks screens show what comes next,
  what is being processed, and what is completed. Solomon can press and
  hold a task, drag the task to a new place in the order, and release
  the task there, and Duke keeps the new order.
- Duke carries a voice switch on the face of the application. When the
  voice switch is off, Duke stops speaking — a flip of the switch cuts
  speech off at once, even in the middle of a piece — and everything
  Duke has to say stays as text on the screen. The switch's state
  persists when the application closes, and every flip of the switch is
  receipted to Kevin — Operator.
- Duke wears the DirectDesk identity. The application name is Duke, the
  application identifier is net.directdesk.duke, the colors are the
  DirectDesk colors — navy, teal, and green — and the application icon
  is a clean Duke lettermark. Duke has no mascot.

How to use Duke
---------------

1. Install Duke on an Android telephone.
2. Open Duke and give Duke the model: either let Duke load the model
   file from the telephone's storage, or, once the download address is
   set on the model configuration line in a later version, let Duke
   download the model.
3. Talk to Duke on the chat screen — type a message or use voice input,
   and read or listen to Duke's answer.
4. Flip the voice switch on the face of the application at any time to
   turn Duke's speech on or off.
5. Open the Tasks screens to see what the crew will do next, what the
   crew is processing, and what the crew has completed, and drag tasks
   by a long press to change the order.
6. Open the postbox screen to exchange messages with Duke and Kevin —
   Operator through the Apps Script postbox.

Building Duke
-------------

Duke builds with the Gradle wrapper that travels with this repository,
and the Duke APK Build workflow in GitHub Actions builds the application
package on every dispatch and keeps the finished package as the
Duke_APK_V4 artifact. Version 4 is version code 40, version name 4.

Where Duke comes from
---------------------

Duke is built from the open-source application GemOfGemma by
ajay-sainy, at version 1.0.3, used under the Apache License, Version
2.0. The license travels with this repository in the LICENSE file, and
the provenance is recorded in the code. The base application provided
the on-device inference foundation — the LiteRT-LM engine, the chat
interface, the camera and voice pieces — and the DirectDesk work on
top of that foundation is what makes the application Duke: the
DirectDesk identity and colors, the Duke lettermark icon, the model
configuration line naming the Qwen model, the Apps Script postbox
communication addressed to Duke and Kevin — Operator, the Tasks
screens, and the voice switch on the face of the application.
