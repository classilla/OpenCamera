# OpenCamera hack for Pixel 6 Pro

The Pixel 6 Pro is an expensive phone. There seem to be a significant number of people whose
P6Ps crash -- hard -- trying to use Google Camera with it. Mine is one of them.

After trying to explain to the underpaid Level 1 support advisor that, yes, I'm up to date,
no, uninstalling updates doesn't help, yes, I'm at the current version of Android, no,
factory resetting it doesn't help, no, I'm not an idiot, I got an answer saying it's being
worked on and I can't return the phone. (The February update didn't make any difference.)

The upshot is, the phone I bought for its camera has a camera I can't use, and I can't
even send it back to them.

OpenCamera, however, doesn't crash. It's not ideal, though: there is no support in it
for the 0.7x rear lens, and the zoom slider is a bit tricky to work with. It also doesn't
take pictures as nice, which is to be expected, because Google probably has some secret
sauce in there for the Tensor unit. But it does take pictures, and it doesn't make the
phone reboot.

So this is a patched up OpenCamera that adds a little 1/2/4/8 button on screen that makes
the zoom a little easier to control and mostly gets you using the camera you think you are.
There is some initial code for maybe getting the 0.7x lens working, but that's something 
perhaps for later. It is GPL, like OpenCamera itself.

Currently these patches work only with the Camera2 API, so make sure you set that, or not much will appear to happen.

It is not intended for any other device other than the P6P. I may have screwed up other
features. I may not ever make any more versions. You use it at your own risk. But hey,
I can at least use the camera I paid for. DO NOT ASK OpenCamera (or for that matter, me)
for support for this application. Fix it yourself.

By the way, Google: up yours.

Cameron Kaiser
