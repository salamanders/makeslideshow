# Make a Slideshow

* Do you have a folder full of images and movies and Android Motion Photos/Apple Live Photos?  
* Is it a headache because some are sideways and some are too long?
* Do you want a nice slideshow in movie format that you can play on a loop?

Great!  Load this app in IntelliJ (for Maven and Kotlin support), plop the album into the right folder on your desktop, and run it!


## Required Steps

0. Shoot Live Photos and Motion Photos of a few events and add them all to a single Google Photos album.
1. Download the album from Google Photos
2. Unzip the Photos.zip photos and movies into a folder: `~/Desktop/slideshow/clips`
3. Run the app, which creates a `~/Desktop/slideshow/slideshow.mp4` file.


## Optional steps:

* Include a `~/Desktop/slideshow/credits.png` file
* Move the best clips into `~/Desktop/slideshow/fullscreen` which will run them fullscreen at the end.
* Clean up duplicates using a few scripts:

```bash
# then copy back into the folder to clobber originals
mogrify -auto-orient -path ../rotated *.jpg 

# Get rid of duplicates
for f in *.heic; do
  [ -e "${f%.*}.mov" ] && echo rm -- "$f"
done

for f in *.jpg; do
  [ -e "${f%.*}.mov" ] && echo rm -- "$f"
done

# Convert the remainders
mogrify -format jpg *.heic && rm *.heic
```


