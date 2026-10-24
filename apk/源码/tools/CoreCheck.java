import cn.local.bullseye.TicketPolicy;
import cn.local.bullseye.LowBalanceControl;
import cn.local.bullseye.TicketDigits;
import static cn.local.bullseye.TicketPolicy.Action.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import java.io.File;

/** Runs with JDK 17; optional arguments are the private x1 and insufficient-x5 screenshots. */
public class CoreCheck {
    static void expect(Object actual,Object expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args)throws Exception {
        TicketPolicy p=new TicketPolicy();
        expect(p.read(9),WAIT);expect(p.read(9),START);expect(p.remaining,9);
        p.started();expect(p.remaining,9); // no estimated deduction on Start
        p.reset();expect(p.read(4),WAIT);expect(p.read(4),CHECK_BUTTON);
        expect(p.button(5),SWITCH);p.switched();expect(p.button(1),START);
        for(int n=3;n>=1;n--){p.reset();expect(p.read(n),WAIT);expect(p.read(n),CHECK_BUTTON);expect(p.button(1),START);}
        p.reset();expect(p.read(0),WAIT);expect(p.read(0),DONE);expect(p.remaining,0);
        p.reset();expect(p.read(0),WAIT);expect(p.read(5),WAIT);expect(p.read(5),START);
        p.reset();expect(p.read(null),WAIT);expect(p.read(null),WAIT);expect(p.read(null),READ_ERROR);
        p.reset();expect(p.button(0),WAIT);expect(p.button(0),WAIT);expect(p.button(0),BUTTON_ERROR);
        p.reset();expect(p.button(5),SWITCH);p.switched();expect(p.button(5),SWITCH);p.switched();expect(p.button(5),SWITCH_ERROR);
        p.reset();expect(p.button(1),START);expect(p.read(1),WAIT);expect(p.read(1),CHECK_BUTTON);
        System.out.println("Ticket policy checks passed");
        if(TicketDigits.single(new int[80*40],80,40)!=null)throw new AssertionError("Blank count is not zero");
        for(int i=0;i<args.length;i++){
            BufferedImage original=ImageIO.read(new File(args[i]));
            for(double scale:new double[]{.5,.75,1,1.5}){
                int w=(int)(original.getWidth()*scale),h=(int)(original.getHeight()*scale);
                BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics();g.drawImage(original,0,0,w,h,null);g.dispose();
                int[] pixels=image.getRGB(0,0,w,h,null,0,w);
                int cx=(int)(840*scale),cy=(int)(635*scale),cw=(int)(129*scale),ch=(int)(93*scale);
                expect(TicketDigits.single(image.getRGB(cx,cy,cw,ch,null,0,cw),cw,ch),1);
                LowBalanceControl.Hit hit=LowBalanceControl.find(pixels,w,h,540/1080f,2036/2400f,245/1080f,290/2400f);
                if(hit==null)throw new AssertionError("Control not found at scale "+scale);
                expect(hit.value,i==0?1:5);
                if(Math.abs(hit.x-.81)>.02||Math.abs(hit.y-.862)>.02)throw new AssertionError("Wrong click point");
                // Obscuring the button must prevent any guessed click.
                g=image.createGraphics();g.setColor(java.awt.Color.BLACK);g.fillRect((int)(.7*w),(int)(.8*h),(int)(.25*w),(int)(.12*h));g.dispose();
                pixels=image.getRGB(0,0,w,h,null,0,w);
                if(LowBalanceControl.find(pixels,w,h,540/1080f,2036/2400f,245/1080f,290/2400f)!=null)throw new AssertionError("Covered button was accepted");
            }
        }
        if(args.length>0)System.out.println("Real screenshot controls and occlusion checks passed at four sizes");
    }
}
