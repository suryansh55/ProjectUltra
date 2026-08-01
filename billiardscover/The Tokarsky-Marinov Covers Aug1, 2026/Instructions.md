# Remarks

In these instructions, terminology described in the paper is used liberally.

# Loading and Removing Covers

To load a cover, select the desired angles you would like to see the cover of. Note: To display the 112.2-112.4 and more covers and the 'All' cover, you must have a machine with at least 10GB free RAM.

To remove a cover and clear any sections, press the 'Clear' button.

# Navigation

You can pan around the viewer by clicking and dragging in the map.

In the menu, there are 4 radio buttons that can be chosen. 'Select' will give information about whatever you clicked on. See the next section of instruction for this. 'Magnify' will zoom to the point you selected. The point you clicked will now be the center of the screen. 'Demagnify' is then of course the same, but zooming out. 'Center' will center the screen on wherever you clicked. The 'Zoom Scale' box is the amount by which to magnify and demagnify by.

To undo or redo navigation actions, the 'Backward' and 'Forward' buttons. You can also press 'Reset' to bring the map view back to where it was when you opened the program.

# Getting Information about Code Sequences

Click on a cover square or the grey infinite pattern region while having the 'Select' radio button chosen. This will cause an info box to come up on the left side, underneath the controls. Here you will  find the code sequence that covers that square or section of the infinite pattern. If the square is covered by a triple, there will of course be three code sequences listed. When clicking on the infinite pattern, there will again be three code sequences listed. These are the stable element of the infinite pattern which you have clicked on, and the two unstable elements which surround it.

You will also see a checkbox with the lable 'Draw' next to each code sequence. Clicking this will cause the "exact" region corresponding to the code sequence to be drawn on screen. Clicking it again will cause the bounding polygon to be drawn. You can click it a final time to reset and have no image drawn. Reminder: this region is only as exact as a computer can draw on screen, and is for visual demonstration  purposes only. The proof is not dependant on this image. Also note that this  drawing process will make the program lag a bit.

To see more detailed info about a code sequence, you can press the button  labeled 'Info'. After entering the code sequence at the top, you will see the bounding polygon and equations of exact region. If the code is unstable, there will also be the equation of the line of triangles which work.

# Viewing the Structure of the Cover

It may be of intrest to you to see how which squares are covered by each code sequence. For this reason, you may select a color after clicking on a square. This will change that square and all other squares that are covered by the same code sequence to that color. Additionally, you can turn the 'Cycle' check box on. This will cause the colors of the cover squares to change as you click on them, with different colors every time. This will allow you to see how the cover is composed.

So far, it is not known whether every periodic path will define a connected region. However, so far every region we have encountered has been star-shaped. You might notice that some sets of cover squares with the same code sequence are disjoint, but this is not because the sequence's region is disjoint. Because there is some overlap in our set of codes, a different code sequence simply filled in some cover squares that would have been filled by another first, causeing it to appear disjoint.

# Checking the Cover

The best way to check the cover is to click on one square, and then press "Check this Square". You will be presented with info about the square and the code sequence that covers it. The coordinates of the square will be in rational coordinates, and the exact equations of the code are given. In the paper, the origin of these equations and the way we checked every square is explained, but to verify for yourself you should confirm that the region where every equation is positive contains the entire square.

'Check Cover' is an automation of this process, it will verify that the equations of the code sequence assigned to each square are positive over it. Due to the size of the proof, in order to trust that the checker is indeed checking the squares correctly, we recommend that you read through the code. The details about how our check works and is rigorous are in the paper.

Checking the whole cover can take a long time, depending on your machine. The first cover, 100-105, will run in less than 5 minutes. It is not recommended that you run any of the other ones unless you are prepared to let it run for quite awhile. The 105-110 cover will take several hours on a standard computer. The rest will take days or longer.

You can also check any of the other individual covers if you wish.





