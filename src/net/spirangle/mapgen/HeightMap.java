package net.spirangle.mapgen;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import net.spirangle.mapgen.util.ProgressHandler;
import net.spirangle.mapgen.util.SimplexNoise;

/**
 * 16 bit grayscale image. All height values for each point will be between 0.0 and 1.0
 */
public class HeightMap {

	private double[][] heightArray;
	private int mapSize;
	private double mapResolution;
	private double landscapeResolution;
	private int iterations;
	private boolean moreLand;
	private int minimumEdge;
	private int maxHeight;
	private int compression;
	private int peaks;
	private int borderCutoff;
	private double borderNormalize;
	private double singleDirt;

	private long heightSeed;
	private long normalizeSeed;
	private long fractureSeed;

	private BufferedImage heightImage;

	public HeightMap(int mapSize,double mapResolution,double landscapeResolution,int iterations,int minimumEdge,int borderWeight,int maxHeight,
	                 int compression,int peaks,boolean moreLand) {
		this.mapSize              = mapSize;
		this.mapResolution        = mapResolution;
		this.landscapeResolution  = landscapeResolution;
		this.iterations           = iterations;
		this.minimumEdge          = minimumEdge;
		this.maxHeight            = maxHeight;
		this.compression          = compression;
		this.peaks                = peaks;
		this.moreLand             = moreLand;

		this.heightArray          = new double[mapSize][mapSize];
		this.borderCutoff         = (int)(mapSize/Math.abs(borderWeight));
		this.borderNormalize      = (float)(1.0/borderCutoff);

		this.singleDirt           = 1.0/maxHeight;

		this.heightSeed           = 0L;
		this.normalizeSeed        = 0L;
		this.fractureSeed         = 0L;
	}

	public void setSeeds(long height,long normalize,long fracture) {
		heightSeed     = height;
		normalizeSeed  = normalize;
		fractureSeed   = fracture;
	}

	boolean importHeightImage(String txtName,String fileName) {
		File imageFile = new File("./maps/"+txtName+"/"+fileName);
		if(!imageFile.exists()) return false;
		MainWindow.log("Importing HeightMap...");
		try {
			heightImage = ImageIO.read(imageFile);
		} catch(IOException e) {
			MainWindow.log("Importing HeightMap failed:"+e.getMessage());
			heightImage = null;
			return false;
		}
		if(heightImage.getHeight()!=heightImage.getWidth()) {
			JOptionPane.showMessageDialog(null,"The map must be square!","Error",JOptionPane.ERROR_MESSAGE);
		} else if(heightImage.getHeight()!=mapSize || heightImage.getWidth()!=mapSize) {
			JOptionPane.showMessageDialog(null,"The image size does not match your map size! "+heightImage.getHeight(),"Error",JOptionPane.ERROR_MESSAGE);
		} else {
			long startTime = System.currentTimeMillis();
			try {
				DataBuffer buffer = heightImage.getRaster().getDataBuffer();
				int rgb;
				if(buffer instanceof DataBufferByte) {
					byte[] pixels = ((DataBufferByte)buffer).getData();
					int width = heightImage.getWidth();
					int height = heightImage.getHeight();
					if(heightImage.getAlphaRaster()!=null) {
						int pixelLength = 4;
						for(int pixel=0,x=0,y=0; pixel<pixels.length; pixel+=pixelLength) {
							rgb  = ((int)pixels[pixel+1]&0xff);       // blue
							rgb += (((int)pixels[pixel+2]&0xff)<<8);  // green
							rgb += (((int)pixels[pixel+3]&0xff)<<16); // red
							setHeight(x,y,(float)((double)rgb/16777216.0d),false);
							++x;
							if(x==width) {
								x = 0;
								++y;
							}
						}
					} else {
						int pixelLength = 3;
						for(int pixel=0,x=0,y=0; pixel<pixels.length; pixel+=pixelLength) {
							rgb  = ((int)pixels[pixel]&0xff);         // blue
							rgb += (((int)pixels[pixel+1]&0xff)<<8);  // green
							rgb += (((int)pixels[pixel+2]&0xff)<<16); // red
							setHeight(x,y,(float)((double)rgb/16777216.0d),false);
							++x;
							if(x==width) {
								x = 0;
								++y;
							}
						}
					}
				} else {
					for(int x=0; x<mapSize; ++x) {
						for(int y=0; y<mapSize; ++y) {
							rgb = buffer.getElem(x+y*mapSize);
							setHeight(x,y,(rgb/65536.0f),false);
						}
					}
				}
				MainWindow.log("HeightMap Import completed in "+(System.currentTimeMillis()-startTime)+"ms.");
				return true;
			} catch(Exception e) {
				MainWindow.log("Importing HeightMap failed: "+e.getMessage());
			}
		}
		heightImage = null;
		return false;
	}

	void exportHeightImage(String txtName,String fileName) {
		File imageFile = new File("./maps/"+txtName+"/"+fileName);
		BufferedImage bufferedImage = new BufferedImage(mapSize,mapSize,BufferedImage.TYPE_USHORT_GRAY);
		WritableRaster wr = (WritableRaster)bufferedImage.getRaster();
		double[] array = new double[mapSize*mapSize];
		for(int x=0; x<mapSize; ++x) {
			for(int y=0; y<mapSize; ++y) {
				array[x+y*mapSize] = (getHeight(x,y)*65535);
			}
		}
		wr.setPixels(0,0,mapSize,mapSize,array);
		bufferedImage.setData(wr);
		try {
			if(!imageFile.exists()) imageFile.mkdirs();
			ImageIO.write(bufferedImage,"png",imageFile);
		} catch(IOException e) {
			JOptionPane.showMessageDialog(null,"Unable to create heightmap file.","Error",JOptionPane.ERROR_MESSAGE);
			return;
		}
	}

	double maxDiff(int x,int y) {
		double neighbours[] = new double[4];
		double currentTile = heightArray[x][y];
		neighbours[0] = heightArray[clamp(x-1,0,mapSize-1)][y];
		neighbours[1] = heightArray[x][clamp(y-1,0,mapSize-1)];
		neighbours[2] = heightArray[clamp(x+1,0,mapSize-1)][y];
		neighbours[3] = heightArray[x][clamp(y+1,0,mapSize-1)];
		double maxDiff = 0.0;
		double diff;
		for(int i=0; i<=3; ++i) {
			diff = currentTile-neighbours[i];
			if(diff>maxDiff) maxDiff = diff;
		}
		return maxDiff;
	}

	/**
	 *  Generates a full heightmap with the current instance's set values.
	 *  Clamps the heightmap heights for the last iteration only.
	 */
	void generateHeights(ProgressHandler progress) {
		MainWindow.log("HeightMap seed set to: "+heightSeed);
		long startTime = System.currentTimeMillis();
		for(int i=0; i<iterations; ++i) {
			SimplexNoise.genGrad(heightSeed+i);
			int progressValue = (int)((float)i/iterations*99f); 
			progress.update(progressValue);
			double iRes = (i<3? mapResolution : landscapeResolution)/Math.pow(2,i-1);
			double str = Math.pow(2,i-1)*2.0;
			for(int x=0; x<mapSize; ++x)
				for(int y=0; y<mapSize; ++y)
					setHeight(x,y,getHeight(x,y)+SimplexNoise.noise(x/iRes,y/iRes)/str,(i==iterations-1));
		}
		double normalize = normalizeHeights();
		MainWindow.log("HeightMap Generation ("+mapSize+") completed in "+(System.currentTimeMillis()-startTime)+"ms.");
	}

	private double normalizeHeights() {
		long startTime = System.currentTimeMillis();
		double h;
		double maxHeight = 0.0d;
		double h1 = 1.0d+((double)compression/100.0d);
		double h2 = 1.0d-(1.0d/h1);
		double m1 = 0.05d;
		double m2 = 1.0d/m1;
		double m3 = (m1+h2)/m1;
		for(int i=0; i<mapSize; ++i)
			for(int j=0; j<mapSize; ++j) {
				h = getHeight(i,j);
				if(h>maxHeight) maxHeight = h;
			}
		maxHeight *= h1;
		SimplexNoise.genGrad(normalizeSeed);
		double normalize = 1.0d/maxHeight;
		double n;
		double iRes = landscapeResolution/Math.pow(2.0d,2.0d);
		double str = Math.pow(2.0d,2.0d)*2.0d;
		for(int i=0; i<mapSize; ++i)
			for(int j=0; j<mapSize; ++j) {
				n = normalize-(SimplexNoise.noise(i/iRes,j/iRes)/str)*normalize;
				h = getHeight(i,j)*n;
				h = h*h*h;
				if(h<=m1) h = h*(2.0d-h*m2)*m3;
				else h += h2;
				setHeight(i,j,h,false);
			}
		MainWindow.log("HeightMap Normalization ("+mapSize+") completed in "+(System.currentTimeMillis()-startTime)+"ms.");
		return normalize;
	}

	void erode(int iterations,int minSlope,int maxSlope,int sedimentMax,ProgressHandler progress) {
		long startTime = System.currentTimeMillis();
		for(int i=0; i<iterations; ++i) {
			int progressValue = (int)((float)i/iterations*99f); 
			progress.update(progressValue);
			erodeArea(0,0,mapSize,minSlope,maxSlope,sedimentMax);
		}
		MainWindow.log("HeightMap Erosion ("+iterations+") completed in "+(System.currentTimeMillis()-startTime)+"ms.");
	}

	void erodeArea(int x,int y,int size,int minSlope,int maxSlope,int sedimentMax) {
		double currentTile;
		double neighbours[] = new double[4];
		for(int i=Math.max(0,x); i<Math.min(mapSize,x+size); ++i) {
			for(int j=Math.max(0,y); j<Math.min(mapSize,y+size); ++j) {
				currentTile   = heightArray[i][j];
				neighbours[0] = heightArray[clamp(i-1,0,mapSize-1)][j];
				neighbours[1] = heightArray[i][clamp(j-1,0,mapSize-1)];
				neighbours[2] = heightArray[clamp(i+1,0,mapSize-1)][j];
				neighbours[3] = heightArray[i][clamp(j+1,0,mapSize-1)];
				int lowest = 0;
				double maxDiff = 0.0;
				for(int k=0; k<=3; ++k) {
					double diff = currentTile-neighbours[k];
					if(diff>maxDiff) {
						maxDiff = diff;
						lowest = k;
					}
				}
				double sediment = 0.0;
				if(maxDiff>minSlope*singleDirt && maxDiff<maxSlope*singleDirt) {
					sediment = (sedimentMax*singleDirt)*maxDiff;
					currentTile -= sediment;
					neighbours[lowest] += sediment;
				}
				setHeight(i,j,currentTile,false);
				setHeight(clamp(i-1,0,mapSize-1),j,neighbours[0],false);
				setHeight(i,clamp(j-1,0,mapSize-1),neighbours[1],false);
				setHeight(clamp(i+1,0,mapSize-1),j,neighbours[2],false);
				setHeight(i,clamp(j+1,0,mapSize-1),neighbours[3],false);
			}
		}
	}

	void fracture(int fracture,ProgressHandler progress) {
		if(fracture==0) return;
		long startTime = System.currentTimeMillis();
		Random rnd = new Random(fractureSeed);
		double currentTile;
		double peaks = 1.0d-(this.peaks/100.0d);
		double alt;
		double frac = fracture*singleDirt;
		for(int i=0; i<mapSize; ++i)
			for(int j=0; j<mapSize; ++j) {
				currentTile = heightArray[i][j];
				alt = currentTile>=peaks? 5.0d : 0.5d;
				currentTile += (rnd.nextDouble()-0.5d)*frac*alt;
				setHeight(i,j,currentTile,false);
			}
		double maxHeight = 0.0f;
		double h;
		for(int i=0; i<mapSize; ++i)
			for(int j=0; j<mapSize; ++j) {
				h = getHeight(i,j);
				if(h>maxHeight) maxHeight = h;
			}
		double normalize = 1.0f/(maxHeight-singleDirt*2.0d);
		for(int i=0; i<mapSize; ++i)
			for(int j=0; j<mapSize; ++j) {
				h = getHeight(i, j);
				setHeight(i,j,h*normalize,false);
			}
		MainWindow.log("HeightMap Fracturing completed in "+(System.currentTimeMillis()-startTime)+"ms.");
	}

	double getHeight(int x,int y) {
		return heightArray[x][y];
	}

	/**
	 * @param x Location x
	 * @param y Location y
	 * @param newHeight Height to set the location to
	 * @param clamp Whether to clamp the location's height depending on x/y and the border cutoff (Constants.BORDER_WEIGHT)
	 */
	private void setHeight(int x,int y,double newHeight,boolean clamp) {
		if(newHeight<(moreLand? -1d : 0)) newHeight = (moreLand? -1d : 0);
		if(newHeight>1d) newHeight = 1d;
		heightArray[x][y] = newHeight;
		if(clamp) {
			if(moreLand) heightArray[x][y] = (heightArray[x][y]+1)*0.5d;
			if(x<=borderCutoff+minimumEdge || y<=borderCutoff+minimumEdge) {
				if(x<y) heightArray[x][y] *= Math.max(0,((Math.min(x,mapSize-y)-minimumEdge))*borderNormalize);
				else heightArray[x][y] *= Math.max(0,((Math.min(y,mapSize-x)-minimumEdge))*borderNormalize);
			} else if(mapSize-x<=borderCutoff+minimumEdge || mapSize-y<=borderCutoff+minimumEdge) {
				heightArray[x][y] *= Math.max(0,((Math.min(mapSize-x,mapSize-y)-minimumEdge))*borderNormalize);
			}
		}
	}

	int getMaxHeight() {
		return maxHeight;
	}

	int getMapSize() {
		return mapSize;
	}

	double getSingleDirt() {
		return singleDirt;
	}

	public static int clamp(int val,int min,int max) {
		return Math.max(min,Math.min(max,val));
	}

	/** Digs at the (x,y) location until the water depth is met. Each iteration increases the radius of dirt that is dug. */
	/*void createPond(int ox,int oy,double water,int baseWidth,int slope) {
		if(water<=0) water = 0;
		if(slope<=0) slope = 1;
		int size = baseWidth-1;
		while(getHeight(ox,oy)>water) {
			double dig = slope*singleDirt;
			for(int x=ox-size; x<=ox+size; ++x) {
				for(int y=oy-size; y<=oy+size; ++y) {
					if(x<0 || x>=mapSize || y<0 || y>=mapSize || getHeight(x,y)<water) continue;
					if(Math.sqrt(Math.pow(x-ox,2)+Math.pow(y-oy,2))<=size) setHeight(x,y,getHeight(x,y)-dig,false);
				}
			}
			++size;
		}
		for(int i=0; i<size; ++i)
			erodeArea(ox-size,oy-size,size*2+1,0,slope,slope);
	}*/
}
