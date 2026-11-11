package cn.local.winterbear;

/** The game keeps icon proportions: top controls follow width, bottom controls follow the bottom edge. */
final class ReferenceLayout {
    final int width,height;
    ReferenceLayout(int width,int height){this.width=width;this.height=height;}
    static boolean supported(int w,int h){return w>=320&&h>=480&&h>w;}
    float scale(){return width/1080f;}
    int x(int reference){return Math.round(reference*scale());}
    int y(int reference){return reference>=1800?height-Math.round((2400-reference)*scale()):x(reference);}
    boolean original(){return width==1080&&height==2400;}
    // Preserve the original recording matcher on its already supported aspect ratios.
    int feedbackHeight(){return Math.abs(width/(double)height-.45)<=.015?720:Math.round(height*324f/width);}
}
