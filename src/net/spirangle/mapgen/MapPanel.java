package net.spirangle.mapgen;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.JPanel;

/**
 * Panel to render the map in the window. Contains mouse input listeners for interaction.
 */
public class MapPanel extends JPanel {

	private static final long serialVersionUID = -6072723167611034006L;

	private BufferedImage mapImage;

	private MainWindow window;
	private int mapSize;
	private double scale = 0.0f;
	private double minScale = 1.0f;
	private int imageX = 0;
	private int imageY = 0;
	private int startX = 0;
	private int startY = 0;
	private int markerOffsetX = 0;
	private int markerOffsetY = 0;
	private boolean showMarker = false;
	private boolean showGrid = false;
	private int gridSize = 1;
//	private boolean isBiomePaintingMode = false;

	public MapPanel(MainWindow w) {
		super();
		window = w;
		this.setMapSize(1024);
		this.addMouseWheelListener(new MouseAdapter() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				startX = e.getX();
				startY = e.getY();
				double ratioX = ((startX-imageX)/scale/mapSize);
				double ratioY = ((startY-imageY)/scale/mapSize);
				double delta = 0.1f * e.getPreciseWheelRotation();
				if(e.isShiftDown())
					delta *= 2;
				int preH = getImageHeight();
				int preW = getImageWidth();
				scale -= delta;
				if(scale <= minScale)
					scale = minScale;
				int deltaX = (int)((getImageWidth() - preW));
				int deltaY = (int)((getImageHeight() - preH));
				imageX -= (int)(deltaX*ratioX);
				imageY -= (int)(deltaY*ratioY);
				checkBounds();
				revalidate();
				repaint();
			}
		});
		this.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				updateScale();
				checkBounds();
			}
		});
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				startX = e.getX();
				startY = e.getY();
				/*if(e.getButton()==MouseEvent.BUTTON1 && isBiomePaintingMode) {
					if(!window.actionReady()) return;
					new Thread() {
						@Override
						public void run() {
							int paintPosX = (int)((startX-imageX)/scale);
							int paintPosY = (int)(((startY-imageY)/scale));
//							window.actionSeedBiome(new Point(paintPosX,paintPosY));
						}
					}.start();
				} else */if(e.getButton()==MouseEvent.BUTTON3) {
					markerOffsetX = (int)((startX-imageX)/scale);
					markerOffsetY = (int)((startY-imageY)/scale);
					showMarker = !showMarker;
					window.updateMapCoords((int)((markerOffsetX)),(int)(mapSize-(markerOffsetY)),showMarker);
					repaint();
				}
			}
		});
		this.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				     if(e.getX()<startX) imageX -= (startX-e.getX());
				else if(e.getX()>startX) imageX += (e.getX()-startX);
				     if(e.getY()<startY) imageY -= (startY-e.getY());
				else if(e.getY()>startY) imageY += (e.getY()-startY);
				startX = e.getX();
				startY = e.getY();
				checkBounds();
				repaint();
			}
			@Override
			public void mouseMoved(MouseEvent e) {
				super.mouseMoved(e);
			}
		});

	}

/*	void setPaintingMode(boolean mode) {
		isBiomePaintingMode = mode;
	}*/

	void showGrid(boolean show) {
		showGrid = show;
		repaint();
	}

	void updateScale() {
		if(this.getWidth()<this.getHeight()) this.minScale = (double)this.getWidth()/(double)mapImage.getWidth();
		if(this.getHeight()<this.getWidth()) this.minScale = (double)this.getHeight()/(double)mapImage.getHeight();
		if(this.scale<this.minScale) this.scale = this.minScale;
	}

	private int getImageWidth() {
		return (int)Math.round(this.mapImage.getWidth()*this.scale);
	}

	private int getImageHeight() {
		return (int)Math.round(this.mapImage.getHeight()*this.scale);
	}

	void checkBounds() {
		int wH = this.getHeight();
		int wW = this.getWidth();
		int iH = this.getImageHeight();
		int iW = this.getImageWidth();
		int minY = wH-iH;
		int minX = wW-iW;
		     if(wW>iW)       imageX = (wW/2)-(iW/2);
		else if(imageX<minX) imageX = minX;
		else if(imageX>0)    imageX = 0;
		     if(wH>iH)       imageY = (wH/2)-(iH/2);
		else if(imageY<minY) imageY = minY;
		else if(imageY>0)    imageY = 0;
	}

	@Override
	public void paintComponent(Graphics g) {
		g.setColor(Color.BLACK);
		g.fillRect(0,0,this.getWidth(),this.getHeight());
		g.drawImage(this.mapImage,imageX,imageY,getImageWidth(),getImageHeight(),null);
		if(showGrid) {
			double gridScale = mapSize/gridSize*scale;
			g.setColor(Color.CYAN);
			for(int x=0; x<=gridSize; ++x)
				g.drawLine(imageX,imageY+(int)(x*gridScale),imageX+(int)(mapSize*scale),(int)(imageY+x*gridScale));
			for(int y=0; y<=gridSize; ++y)
				g.drawLine((int)(imageX+y*gridScale),imageY,(int)(imageX+y*gridScale),imageY+(int)(mapSize*scale));
		}
		int riverMarker = 12;
		g.setColor(Color.yellow);
		if(showMarker) {
			g.setColor(Color.RED);
			g.fillOval((int)((markerOffsetX*scale)+imageX)-4,(int)((markerOffsetY*scale)+imageY)-4,8,8);
		}
	}

	void setMapSize(int newMapSize) {
		mapSize = newMapSize;
		if(mapImage!=null) mapImage.flush();
		mapImage = new BufferedImage(mapSize,mapSize,BufferedImage.TYPE_BYTE_GRAY);
		updateScale();
		checkBounds();
		scale = minScale;
	}

	void setMapImage(BufferedImage newImage) {
		int newSize = newImage.getWidth();
		int oldSize = mapImage.getWidth();
		if(mapImage!=null) mapImage.flush();
		mapImage = newImage;
		updateScale();
		checkBounds();
		if(oldSize!=newSize) scale = minScale;
	}

	BufferedImage getMapImage() {
		return mapImage;
	}

	void setGridSize(int size) {
		if(size<=0) size = 1;
		gridSize = size;
		repaint();
	}

	int getMapSize() {
		return mapSize;
	}
}
