; FIRST-PART -- synthetic demo for Intraspect, not a machine program
; Stock: diameter 80 mm, length 160 mm. X is programmed as diameter.
; Library: T1 D1, finishing tool, nose radius 0.4 mm, position 3.
N10 WORKPIECE(,,,"CYLINDER",0,0,-160,-160,80)
N20 G18 G90 G95 G40 DIAMON G500
N30 T1 D1
N40 G97 S400 M3
N50 G0 X84 Z3
N60 G1 X70 F0.4
N70 G1 Z-45
N80 G1 X76
N90 G1 Z-100
N100 G1 X80 Z-110
N110 G1 Z-160
N120 G1 X84
N130 G0 X100 Z10
N140 M5
N150 M30
