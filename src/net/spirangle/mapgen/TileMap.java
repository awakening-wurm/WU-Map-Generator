package net.spirangle.mapgen;

import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import com.wurmonline.mesh.Tiles.Tile;

import net.spirangle.mapgen.util.Constants;
import net.spirangle.mapgen.util.ProgressHandler;
import net.spirangle.mapgen.util.SimplexNoise;

public class TileMap {

	public static final int[] alignX = {  0, 1, 0,-1, 1, 1,-1,-1 };
	public static final int[] alignY = { -1, 0, 1, 0,-1, 1, 1,-1 };

	private class Biome {
		Biome[] area;
		Tile type;
		Tile ore;
		short rock;
		short dirt;
		short surface;
		short slope;
		float inland;
		float temp;
		float env;
		float climate;
		short flower;
		boolean isAbove(int height) {
			return surface>=height &&
			       (area[1]==null || area[1].surface>=height) &&
			       (area[2]==null || area[2].surface>=height) &&
			       (area[6]==null || area[6].surface>=height);
		}
		boolean isBelow(int depth) {
			return surface<=depth &&
			       (area[1]==null || area[1].surface<=depth) &&
			       (area[2]==null || area[2].surface<=depth) &&
			       (area[6]==null || area[6].surface<=depth);
		}
		int getHoleDepth() {
			int h = 0,s;
			if((area[0]!=null && area[0].dirt<5) || (area[3]!=null && area[3].dirt<5) || (area[4]!=null && area[4].dirt<5)) return 0;
			for(int i=0; i<4; ++i)
				if(area[i]!=null) {
					s = area[i].surface-surface;
					if(s<=0) return 0;
					if(h==0 || s<h) h = s;
				}
			return h;
		}
	}

	private Biome[][] biomes;

	private Random biomeRandom;
	private HeightMap heightMap;
	private Tile[][] typeMap;
	private Tile[][] oreTypeMap;
	private short[][] oreResourceMap;
	private short[][] dirtMap;
	private double singleDirt;
	private double waterHeight;
	private double snowHeight;
	private double tundraHeight;
	private double packedDirtHeight;
	private boolean hasOres;
	private long dirtDropProgress;
	private long biomeSeed;
	private short[][] flowerMap;
	public static HashMap<Color,Tile> colorMap;

	public TileMap(HeightMap heightMap) {
		this.heightMap      = heightMap;
		this.singleDirt     = heightMap.getSingleDirt();
		this.typeMap        = new Tile[heightMap.getMapSize()][heightMap.getMapSize()];
		this.flowerMap      = new short[heightMap.getMapSize()][heightMap.getMapSize()];
		this.oreTypeMap     = new Tile[heightMap.getMapSize()][heightMap.getMapSize()];
		this.oreResourceMap = new short[heightMap.getMapSize()][heightMap.getMapSize()];
		this.dirtMap        = new short[heightMap.getMapSize()][heightMap.getMapSize()];
		this.hasOres        = false;
		setupTileColorMap();
	}

	void dropDirt(final int dirtCount,final int maxSlope,final int maxDiagSlope,final int maxDirtHeight,final double cliffRatio,
	              final boolean landSlide,final ProgressHandler progress) {
		final double maxSlopeHeight     = maxSlope*singleDirt;
		final double maxDiagSlopeHeight = maxDiagSlope*singleDirt;
		final double maxHeight          = maxDirtHeight*singleDirt;
		final double taperHeight        = maxHeight-(dirtCount*singleDirt);
		final int mapSize               = heightMap.getMapSize();
		final long startTime            = System.currentTimeMillis();

		dirtDropProgress = 0;

		final int s     = heightMap.getMapSize();
		final int mod   = s>>5;
		final float div = (float)(s*s*dirtCount);

		class Iteration implements Runnable {
			int sizex,sizey;
			int ix,iy;

			public Iteration(int ix,int iy,int sizex,int sizey) {
				this.ix    = ix;
				this.iy    = iy;
				this.sizex = sizex;
				this.sizey = sizey;
			}

			public void run() {
				for(int x=ix; x<ix+sizex; ++x) {
					for(int y=iy; y<iy+sizey; ++y) {
						if(x%mod==0 && y%mod==0) {
							dirtDropProgress += mod*mod;
							progress.update((int)(100.0f*(float)dirtDropProgress/div));
						}
						if(dirtMap[x][y]>=findDropAmount(x,y,maxSlope,maxDiagSlope,dirtCount,cliffRatio)) continue;
						if(getTileHeight(x,y)>maxHeight) continue;
						if(getTileHeight(x,y)>taperHeight)
							if(getTileHeight(x,y)/singleDirt+dirtCount/2>maxDirtHeight) continue;
						if(landSlide) {
							Point dropTile = findDropTile(x,y,maxSlopeHeight,maxDiagSlopeHeight);
							addDirt((int)dropTile.getX(),(int)dropTile.getY(),1);
						} else {
							Point dropTile = new Point(x,y);
							addDirt((int)dropTile.getX(),(int)dropTile.getY(),1);
						}
					}
				}
			}
		}

		Thread firstThreads[] = new Thread[dirtCount];
		for(int i=0; i<dirtCount; ++i) {
			firstThreads[i] = new Thread(new Iteration(0,0,mapSize,mapSize));
			firstThreads[i].start();
		}
		for(Thread thread : firstThreads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
		}

		// Place snow on mountain peaks
		for(int x=0; x<mapSize; ++x)
			for(int y=0; y<mapSize; ++y) {
				if(getTileHeight(x,y)>=snowHeight) {
					if(getType(x,y)==Tile.TILE_ROCK) dirtMap[x][y]++;
					setType(x,y,Tile.TILE_SNOW);
				}
			}

		MainWindow.log("Dirt Dropping (" + dirtCount + ") completed in " + (System.currentTimeMillis() - startTime) + "ms.");
	}


	private int findDropAmount(int x,int y,double maxSlope,double maxDiagSlope,int dirtCount,double cliffRatio) {
		double slope    = heightMap.maxDiff(x,y)/singleDirt*cliffRatio;
		double slopeMax = (maxSlope+maxDiagSlope)/2.0;
		return Math.max(0,(int)(dirtCount-((dirtCount/slopeMax)*slope)));
	}

	void erode(int iterations,int minSlope,int maxSlope,int sedimentMax,ProgressHandler progress) {
		long startTime = System.currentTimeMillis();
		final int mapSize = heightMap.getMapSize();
		for(int iter=0; iter<iterations; ++iter) {
			progress.update((int)((float)iter/iterations*99f));
			erodeArea(0,0,mapSize,minSlope,maxSlope,sedimentMax);
		}
		MainWindow.log("TileMap Erosion (" + iterations + ") completed in " + (System.currentTimeMillis() - startTime) + "ms.");
	}

	void erodeArea(int x,int y,int size,int minSlope,int maxSlope,int sedimentMax) {
		final int mapSize = heightMap.getMapSize();
		final double h2d = 0.1d/singleDirt;
		double currentHeight,height;
		int currentDirt,dirt,sediment;
		int i,j,m = Math.min(mapSize,x+size),n = Math.min(mapSize,y+size),lx = 0,ly = 0;
		Point p;
		for(i=Math.max(0,x); i<m; ++i) {
			for(j=Math.max(0,y); j<n; ++j) {
				currentHeight = getTileHeight(i,j);
				currentDirt = dirtMap[i][j];
				if(currentHeight>=snowHeight || currentDirt==0) continue;
				p = findErodeTile(i,j);
//MainWindow.log("erodeArea(p: "+p+", x: "+(p!=null? p.x : -1)+", y: "+(p!=null? p.y : -1)+", i: "+i+", j: "+j+")");
				if(p==null) continue;
				lx = p.x;
				ly = p.y;
				height = getTileHeight(lx,ly);
				dirt = dirtMap[lx][ly];
//MainWindow.log("erodeArea(i: "+i+", j: "+j+", lx: "+lx+", ly: "+ly+", currentHeight: "+currentHeight+", height: "+height+")");
				if(currentHeight<=height) continue;
				sediment = (int)Math.ceil((currentHeight-height)*h2d);
				if(sediment>currentDirt) sediment = currentDirt;
				setDirt(i,j,(short)(currentDirt-sediment));
				setDirt(lx,ly,(short)(dirt+sediment));
			}
		}
	}

	void generateOres(double[] rates,ProgressHandler progress) {
		setBiomeSeed(biomeSeed);
		long startTime = System.currentTimeMillis();
		double rand;
		for(int x=0; x<heightMap.getMapSize(); ++x) {
			progress.update((int)((float)x/heightMap.getMapSize()*99f));
			for(int y=0; y<heightMap.getMapSize(); ++y) {
				rand = biomeRandom.nextDouble() * 100;
				     if((rand-=rates[ 0])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL,                	 biomeRandom.nextInt(   20)+40);
				else if((rand-=rates[ 1])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_IRON,       	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 2])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_GOLD,       	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 3])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_SILVER,     	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 4])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_ZINC,       	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 5])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_COPPER,     	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 6])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_LEAD,       	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 7])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_TIN,        	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 8])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_ADAMANTINE, 	 biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[ 9])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ORE_GLIMMERSTEEL, biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[10])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_MARBLE,           biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[11])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_SLATE,            biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[12])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_SANDSTONE,        biomeRandom.nextInt(15000)+90);
				else if((rand-=rates[13])<=0.0) setOreType(x,y,Tile.TILE_CAVE_WALL_ROCKSALT,         biomeRandom.nextInt(15000)+90);
				else                            setOreType(x,y,Tile.TILE_CAVE_WALL,                  biomeRandom.nextInt(   20)+40);
			}
		}
		hasOres = true;
		MainWindow.log("Ore Generation completed in "+(System.currentTimeMillis()-startTime)+"ms.");
	}

	boolean importBiomeImage(String txtName,String fileName) {
		File imageFile = new File("./maps/"+txtName+"/"+fileName);
		if(!imageFile.exists()) return false;
		MainWindow.log("Importing BiomeMap...");
		BufferedImage biomeImage = null;
		try {
			biomeImage = ImageIO.read(imageFile);
		} catch(IOException e) {
			MainWindow.log("Importing BiomeMap failed:"+e.getMessage());
			return false;
		}
		if(biomeImage.getHeight()!=biomeImage.getWidth()) {
			JOptionPane.showMessageDialog(null,"The map must be square!","Error",JOptionPane.ERROR_MESSAGE);
		} else if(biomeImage.getHeight()!=heightMap.getMapSize() || biomeImage.getWidth()!=heightMap.getMapSize()) {
			JOptionPane.showMessageDialog(null,"The image size does not match your map size! "+biomeImage.getHeight(),"Error",JOptionPane.ERROR_MESSAGE);
		} else {
			long startTime = System.currentTimeMillis();
			try {
				DataBuffer buffer = biomeImage.getRaster().getDataBuffer();
				int rgb,r,g,b;
				Tile type;
				if(buffer instanceof DataBufferByte) {
					byte[] pixels = ((DataBufferByte)buffer).getData();
					int width = biomeImage.getWidth();
					int height = biomeImage.getHeight();
					if(biomeImage.getAlphaRaster()!=null) {
						int pixelLength = 4;
						for(int pixel=0,x=0,y=0; pixel<pixels.length; pixel+=pixelLength) {
							rgb  = ((int)pixels[pixel+1]&0xff);       // blue
							rgb += (((int)pixels[pixel+2]&0xff)<<8);  // green
							rgb += (((int)pixels[pixel+3]&0xff)<<16); // red
							rgbToTile(x,y,rgb);
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
							rgbToTile(x,y,rgb);
							++x;
							if(x==width) {
								x = 0;
								++y;
							}
						}
					}
					MainWindow.log("BiomeMap Import completed in "+(System.currentTimeMillis()-startTime)+"ms.");
					return true;
				} else {
					JOptionPane.showMessageDialog(null,"The image must be 24-bit or 32-bit format.","Error",JOptionPane.ERROR_MESSAGE);
				}
			} catch(Exception e) {
				MainWindow.log("Importing BiomeMap failed: "+e.getMessage());
			}
		}
		return false;
	}

	void rgbToTile(int x,int y,int rgb) {
		Tile type;
		switch(rgb) {
			case 0xFFFFFF:type = Tile.TILE_SNOW;break;
			case 0xCCCCCC:type = Tile.TILE_TUNDRA;break;
			case 0xFFFF00:type = Tile.TILE_SAND;break;
			case 0x66FF66:type = Tile.TILE_GRASS;break;
			case 0x009900:type = Tile.TILE_TREE;break;
			case 0x33FF33:type = Tile.TILE_BUSH;break;
			case 0x00FF00:type = Tile.TILE_STEPPE;break;
			case 0xFFCC99:type = Tile.TILE_DIRT;break;
			case 0xCC9966:type = Tile.TILE_DIRT_PACKED;break;
			case 0x999999:type = Tile.TILE_CLAY;break;
			case 0x663300:type = Tile.TILE_PEAT;break;
			case 0x333333:type = Tile.TILE_TAR;break;
			case 0x99FF00:type = Tile.TILE_MOSS;break;
			case 0x00FF99:type = Tile.TILE_MARSH;break;
			case 0xFF00FF:type = Tile.TILE_MYCELIUM;break;
			case 0xFF9900:type = Tile.TILE_LAVA;break;
			default:return;
		}
		if(typeMap[x][y]!=Tile.TILE_GRASS) {
			if(type!=Tile.TILE_MARSH) return;
			double h = getTileHeight(x,y);
			if(h<waterHeight-7.0d*singleDirt) return;
		}
		typeMap[x][y] = type;
	}

	void generateBiomes(double biomeResolution,float biomeDesertTemp,float biomeSteppeTemp,float biomeTundraTemp,float biomeGlacierTemp,
	                    float biomeSubtropicalTemp,float biomeMediterranTemp,float biomeTemperateTemp,int biomePlantDensity,int biomeFlowerDensity,
	                    int biomeBeaches,int biomeClayPits,ProgressHandler progress) {
		int mx,my,sx,sy,ex,ey,x,y,s = heightMap.getMapSize(),t = s>>1,n,i,j,p;
		long startTime = System.currentTimeMillis();
		double iRes,str;
		Tile t1,t2,t3;
		float snow,lat,lon,h,r,c;
		float pd = (float)biomePlantDensity/100.0f,pd1 = pd*0.05f,pd2 = pd-pd1;
		float fd = (float)biomeFlowerDensity/100.0f;
		float beach = (((float)biomeBeaches/100.0f)-0.5f)*0.25f;
		float clay = 0.1f-(((float)biomeClayPits/100.0f)-0.5f)*0.05f;
		float biomePackedDirt = biomeTundraTemp - (biomeTundraTemp-biomeGlacierTemp)*0.85f;
		float minTemp = 0.0f,maxTemp = 0.0f,minEnv = 0.0f,maxEnv = 0.0f,minClimate = 0.0f,maxClimate = 0.0f;
		float e1,c1;
		setBiomeSeed(biomeSeed);
		SimplexNoise.genGrad(biomeSeed);
		biomes = new Biome[t+2][t+2];
		for(mx=0; mx<2; ++mx)
			for(my=0; my<2; ++my) {
				p = mx*50+my*25;
				progress.update(p);
				sx = mx==0? 0 : t-2;
				sy = my==0? 0 : t-2;
				ex = mx==0? t+2 : s;
				ey = my==0? t+2 : s;
				iRes = biomeResolution/Math.pow(2.0d,2.0d);
				str = Math.pow(2.0d,2.0d)*2.0d;
				Biome b;
				minEnv = Float.MAX_VALUE;
				maxEnv = 0.0f;
				for(x=sx; x<ex; ++x)
					for(y=sy; y<ey; ++y) {
						b = new Biome();
						b.type = getType(x,y);
						b.ore = getOreType(x,y);
						b.rock = getRockHeight(x,y);
						b.surface = getSurfaceHeight(x,y);
						b.dirt = (short)(b.surface-b.rock);
						b.slope = 0;
						i = Math.abs(x-t);
						j = Math.abs(y-t);
						b.inland = 1.0f - (float)Math.sqrt(i*i+j*j)/(float)t;
						b.temp = 10.0f;
						b.env = (float)(SimplexNoise.noise(x/iRes,y/iRes)/str);
						if(b.env<minEnv) minEnv = b.env;
						if(b.env>maxEnv) maxEnv = b.env;
						biomes[x-sx][y-sy] = b;
						setFlowerType(x,y,0);
					}
				e1 = 1.0f/(Math.max(Math.abs(minEnv),maxEnv)/0.125f);
				progress.update(p+5);

				iRes = biomeResolution;
				str = 2.0d;
				snow = (float)(snowHeight*heightMap.getMaxHeight());
				minTemp = Float.MAX_VALUE;
				maxTemp = 0.0f;
				minClimate = Float.MAX_VALUE;
				maxClimate = 0.0f;
				for(y=sy; y<ey; ++y) {
					for(x=sx; x<ex; ++x) {
						b = biomes[x-sx][y-sy];
						b.area = getBiomeArea(x-sx,y-sy,t+2);
						b.climate = (float)(SimplexNoise.noise(x/iRes,y/iRes)/str);
						if(b.climate<minClimate) minClimate = b.climate;
						if(b.climate>maxClimate) maxClimate = b.climate;
					}
				}
				c1 = 1.0f/(Math.max(Math.abs(minClimate),maxClimate)/0.5f);
				progress.update(p+10);

				for(y=sy; y<ey; ++y)
					for(x=sx; x<ex; ++x) {
						b = biomes[x-sx][y-sy];
						b.env *= e1;
						b.climate *= c1;
						if(b.type==Tile.TILE_SNOW) continue;
						h = b.getHoleDepth();
						if(h>10) {
							dirtMap[x][y] += (short)(((double)h*0.95d)/(singleDirt*(double)heightMap.getMaxHeight()));
							b.surface = getSurfaceHeight(x,y);
							b.dirt = (short)(b.surface-b.rock);
						}
					}
				progress.update(p+13);

				for(y=sy; y<ey; ++y) {
					lat = (float)y/(float)s;
					for(x=sx; x<ex; ++x) {
						lon = (float)(s-x)/(float)s;
						b = biomes[x-sx][y-sy];
						if(b.surface>=0) {
							h = (float)b.surface/snow;
							h = h*h*h;
							b.temp = (((lat+lon)*0.5f)*22.0f + b.inland*(y<t? -5.0f : 5.0f) + (b.climate+b.env*0.33f)*6.0f)*(1.0f-h) + h*-5.0f;
//							b.temp = (11.0f + b.inland*(y<t? 5.0f : -5.0f) + (b.climate+b.env*0.33f)*6.0f)*(1.0f-h) + h*-5.0f;
							if(b.temp<minTemp) minTemp = b.temp;
							if(b.temp>maxTemp) maxTemp = b.temp;
						}
						if(x>sx && y>sy && x<ex-1 && y<ey-1) {
							b.slope = (short)Math.abs(b.area[6].surface-b.surface);
							n = Math.abs(b.area[1].surface-b.area[2].surface);
							if(n>b.slope) b.slope = (short)n;
						}
					}
				}
				progress.update(p+17);

				for(x=sx; x<ex; ++x) {
					for(y=sy; y<ey; ++y) {
						b = biomes[x-sx][y-sy];
						r = biomeRandom.nextFloat();
						if(b.type==Tile.TILE_ROCK || b.type==Tile.TILE_LAVA) continue;
						if(b.isAbove(0)) {
							if(typeMap[x][y]!=null)
								b.type = typeMap[x][y];

							if(b.type==Tile.TILE_GRASS || b.type==Tile.TILE_TREE || b.type==Tile.TILE_BUSH) {
									  /*if(b.temp>biomeDesertTemp) b.type = Tile.TILE_SAND;
								else if(b.temp>biomeSteppeTemp && b.surface>=80) b.type = Tile.TILE_STEPPE;
								else if(b.temp<biomeGlacierTemp) b.type = Tile.TILE_SNOW;
								else if(b.temp<biomePackedDirt && b.surface>=80) b.type = Tile.TILE_DIRT_PACKED;
								else if(b.temp<biomeTundraTemp && b.surface>=80 && b.type!=Tile.TILE_SAND) b.type = Tile.TILE_TUNDRA;
								else */if(b.surface<80 && b.slope<30 && b.env<beach) b.type = Tile.TILE_SAND;
								else if(b.surface>=5 && b.surface<85 && b.env<beach+0.005f) b.type = Tile.TILE_STEPPE;
								else if(b.surface<180 && b.slope<20 && b.env>clay) b.type = Tile.TILE_CLAY;
								else if(b.surface>=200 && b.surface<1200 && b.slope<25 && b.climate<-0.2f && b.env<-0.11f) b.type = Tile.TILE_PEAT;
								else if(b.surface>=200 && b.surface<1200 && b.slope<25 && b.climate>0.2f && b.env>0.11f) b.type = Tile.TILE_TAR;
//								else if(b.surface<300 && b.slope<10 && b.climate>0.2f && b.env<-0.1f) b.type = Tile.TILE_MARSH;
								else if(b.type==Tile.TILE_TREE && b.surface>=5 && b.surface<1000 && b.slope<20 && b.climate<-0.2f && b.env>0.105f) b.type = Tile.TILE_MOSS;
							}

							     if(b.type==Tile.TILE_TUNDRA && r<0.05f) b.type = Tile.TILE_BUSH_LINGONBERRY;
							else if(b.type==Tile.TILE_TREE) {
								if(b.temp>biomeSubtropicalTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.3f)       b.type = Tile.TILE_BUSH_CAMELLIA;
										else if(r<0.6f)       b.type = Tile.TILE_BUSH_GRAPE;
										else if(r<0.8f)       b.type = Tile.TILE_BUSH_OLEANDER;
										else                  b.type = Tile.TILE_BUSH_THORN;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
											  if(b.env>0.05f)  b.type = Tile.TILE_TREE_CEDAR;
										else if(r>0.5f)       b.type = Tile.TILE_TREE_OLIVE;
										else if(r>0.25f)      b.type = Tile.TILE_TREE_LEMON;
										else                  b.type = Tile.TILE_TREE_ORANGE;
									}
								} else if(b.temp>biomeMediterranTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.1f)       b.type = Tile.TILE_BUSH_CAMELLIA;
										else if(r<0.2f)       b.type = Tile.TILE_BUSH_GRAPE;
										else if(r<0.3f)       b.type = Tile.TILE_BUSH_OLEANDER;
										else if(r<0.5f)       b.type = Tile.TILE_BUSH_THORN;
										else if(r<0.75f)      b.type = Tile.TILE_BUSH_ROSE;
										else                  b.type = Tile.TILE_BUSH_LAVENDER;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
											  if(b.env>0.05f)  b.type = Tile.TILE_TREE_MAPLE;
										else if(b.env<-0.05f) b.type = Tile.TILE_TREE_CHESTNUT;
										else if(r>0.9f)       b.type = Tile.TILE_TREE_OLIVE;
										else if(r>0.85f)      b.type = Tile.TILE_TREE_LEMON;
										else if(r>0.8f)       b.type = Tile.TILE_TREE_ORANGE;
										else if(r>0.7f)       b.type = Tile.TILE_TREE_CEDAR;
										else if(r>0.6f)       b.type = Tile.TILE_TREE_CHESTNUT;
										else if(r>0.5f)       b.type = Tile.TILE_TREE_WALNUT;
										else if(r>0.45f)      b.type = Tile.TILE_TREE_APPLE;
										else if(r>0.4f)       b.type = Tile.TILE_TREE_CHERRY;
										else if(r>0.3f)       b.type = Tile.TILE_TREE_BIRCH;
										else if(r>0.25f)      b.type = Tile.TILE_TREE_LINDEN;
										else if(r>0.2f)       b.type = Tile.TILE_TREE_MAPLE;
										else if(r>0.1f)       b.type = Tile.TILE_TREE_OAK;
										else                  b.type = Tile.TILE_TREE_WILLOW;
									}
								} else if(b.temp>biomeTemperateTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.2f)       b.type = Tile.TILE_BUSH_THORN;
										else if(r<0.3f)       b.type = Tile.TILE_BUSH_ROSE;
										else if(r<0.4f)       b.type = Tile.TILE_BUSH_LAVENDER;
										else if(r<0.6f)       b.type = Tile.TILE_BUSH_HAZELNUT;
										else if(r<0.8f)       b.type = Tile.TILE_BUSH_RASPBERRYE;
										else                  b.type = Tile.TILE_BUSH_BLUEBERRY;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
										if(b.env>=-0.05f) {
												  if(r>0.8f)    b.type = Tile.TILE_TREE_BIRCH;
											else if(r>0.65f)   b.type = Tile.TILE_TREE_CHESTNUT;
											else if(r>0.55f)   b.type = Tile.TILE_TREE_LINDEN;
											else if(r>0.3f)    b.type = Tile.TILE_TREE_MAPLE;
											else if(r>0.2f)    b.type = Tile.TILE_TREE_OAK;
											else if(r>0.1f)    b.type = Tile.TILE_TREE_WALNUT;
											else               b.type = Tile.TILE_TREE_WILLOW;
										} else {
												  if(r>0.6f)    b.type = Tile.TILE_TREE_PINE;
											else if(r>0.2f)    b.type = Tile.TILE_TREE_FIR;
											else               b.type = Tile.TILE_TREE_BIRCH;
										}
									}
								} else {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.2f)       b.type = Tile.TILE_BUSH_THORN;
										else                  b.type = Tile.TILE_BUSH_BLUEBERRY;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
											  if(b.env>0.05f)  b.type = Tile.TILE_TREE_PINE;
										else if(b.env<-0.05f) b.type = Tile.TILE_TREE_FIR;
										else if(r>0.7f)       b.type = Tile.TILE_TREE_PINE;
										else if(r>0.4f)       b.type = Tile.TILE_TREE_FIR;
										else                  b.type = Tile.TILE_TREE_BIRCH;
									}
								}
							} else if(b.type==Tile.TILE_BUSH) {
								if(b.temp>biomeSubtropicalTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.33f)      b.type = Tile.TILE_TREE_OLIVE;
										else if(r<0.66f)      b.type = Tile.TILE_TREE_LEMON;
										else                  b.type = Tile.TILE_TREE_ORANGE;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
											  if(b.env>0.05f)  b.type = Tile.TILE_BUSH_CAMELLIA;
										else if(r>0.7f)       b.type = Tile.TILE_BUSH_CAMELLIA;
										else if(r>0.4f)       b.type = Tile.TILE_BUSH_GRAPE;
										else if(r>0.15f)      b.type = Tile.TILE_BUSH_OLEANDER;
										else                  b.type = Tile.TILE_BUSH_THORN;
									}
								} else if(b.temp>biomeMediterranTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.1f)       b.type = Tile.TILE_TREE_OLIVE;
										else if(r<0.2f)       b.type = Tile.TILE_TREE_LEMON;
										else if(r<0.3f)       b.type = Tile.TILE_TREE_ORANGE;
										else if(r<0.4f)       b.type = Tile.TILE_TREE_CHESTNUT;
										else if(r<0.5f)       b.type = Tile.TILE_TREE_LINDEN;
										else if(r<0.6f)       b.type = Tile.TILE_TREE_MAPLE;
										else if(r<0.7f)       b.type = Tile.TILE_TREE_OAK;
										else if(r<0.8f)       b.type = Tile.TILE_TREE_WILLOW;
										else if(r<0.9f)       b.type = Tile.TILE_TREE_APPLE;
										else                  b.type = Tile.TILE_TREE_CHERRY;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
											  if(b.env>0.05f)  b.type = Tile.TILE_BUSH_GRAPE;
										else if(b.env<-0.05f) b.type = Tile.TILE_BUSH_ROSE;
										else if(r>0.9f)       b.type = Tile.TILE_BUSH_CAMELLIA;
										else if(r>0.8f)       b.type = Tile.TILE_BUSH_OLEANDER;
										else if(r>0.5f)       b.type = Tile.TILE_BUSH_LAVENDER;
										else if(r>0.4f)       b.type = Tile.TILE_BUSH_GRAPE;
										else if(r>0.1f)       b.type = Tile.TILE_BUSH_ROSE;
										else                  b.type = Tile.TILE_BUSH_THORN;
									}
								} else if(b.temp>biomeTemperateTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.1f)       b.type = Tile.TILE_TREE_BIRCH;
										else if(r<0.3f)       b.type = Tile.TILE_TREE_CHESTNUT;
										else if(r<0.4f)       b.type = Tile.TILE_TREE_LINDEN;
										else if(r<0.5f)       b.type = Tile.TILE_TREE_MAPLE;
										else if(r<0.7f)       b.type = Tile.TILE_TREE_OAK;
										else if(r<0.8f)       b.type = Tile.TILE_TREE_WALNUT;
										else                  b.type = Tile.TILE_TREE_WILLOW;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
											  if(b.env<-0.05f) b.type = Tile.TILE_BUSH_THORN;
										else if(r>0.7f)       b.type = Tile.TILE_BUSH_ROSE;
										else if(r>0.5f)       b.type = Tile.TILE_BUSH_LAVENDER;
										else if(r>0.4f)       b.type = Tile.TILE_BUSH_HAZELNUT;
										else if(r>0.2f)       b.type = Tile.TILE_BUSH_RASPBERRYE;
										else                  b.type = Tile.TILE_BUSH_BLUEBERRY;
									}
								} else {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.7f)       b.type = Tile.TILE_TREE_FIR;
										else if(r<0.5f)       b.type = Tile.TILE_TREE_PINE;
										else                  b.type = Tile.TILE_TREE_BIRCH;
									} else if(r<=pd) {
										r = (r-pd1)/pd2;
											  if(b.env>0.05f)  b.type = Tile.TILE_BUSH_BLUEBERRY;
										else if(r>0.3f)       b.type = Tile.TILE_BUSH_BLUEBERRY;
										else                  b.type = Tile.TILE_BUSH_THORN;
									}
								}
							} else if(b.type==Tile.TILE_GRASS) {
								if(b.temp>biomeSubtropicalTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.05f)      b.type = Tile.TILE_TREE_OLIVE;
										else if(r<0.15f)      b.type = Tile.TILE_TREE_LEMON;
										else if(r<0.25f)      b.type = Tile.TILE_TREE_ORANGE;
										else if(r<0.4f)       b.type = Tile.TILE_BUSH_CAMELLIA;
										else if(r<0.6f)       b.type = Tile.TILE_BUSH_GRAPE;
										else if(r<0.8f)       b.type = Tile.TILE_BUSH_OLEANDER;
										else                  b.type = Tile.TILE_BUSH_THORN;
									}
								} else if(b.temp>biomeMediterranTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.05f)      b.type = Tile.TILE_TREE_OLIVE;
										else if(r<0.1f)       b.type = Tile.TILE_TREE_LEMON;
										else if(r<0.15f)      b.type = Tile.TILE_TREE_ORANGE;
										else if(r<0.2f)       b.type = Tile.TILE_TREE_CHESTNUT;
										else if(r<0.25f)      b.type = Tile.TILE_TREE_MAPLE;
										else if(r<0.3f)       b.type = Tile.TILE_TREE_OAK;
										else if(r<0.35f)      b.type = Tile.TILE_TREE_WILLOW;
										else if(r<0.4f)       b.type = Tile.TILE_TREE_APPLE;
										else if(r<0.45f)      b.type = Tile.TILE_TREE_CHERRY;
										else if(r<0.55f)      b.type = Tile.TILE_BUSH_CAMELLIA;
										else if(r<0.6f)       b.type = Tile.TILE_BUSH_OLEANDER;
										else if(r<0.7f)       b.type = Tile.TILE_BUSH_LAVENDER;
										else if(r<0.8f)       b.type = Tile.TILE_BUSH_GRAPE;
										else if(r<0.9f)       b.type = Tile.TILE_BUSH_ROSE;
										else                  b.type = Tile.TILE_BUSH_THORN;
									}
								} else if(b.temp>biomeTemperateTemp) {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.05f)      b.type = Tile.TILE_TREE_BIRCH;
										else if(r<0.1f)       b.type = Tile.TILE_TREE_CHESTNUT;
										else if(r<0.15f)      b.type = Tile.TILE_TREE_MAPLE;
										else if(r<0.2f)       b.type = Tile.TILE_TREE_OAK;
										else if(r<0.25f)      b.type = Tile.TILE_TREE_WALNUT;
										else if(r<0.3f)       b.type = Tile.TILE_TREE_WILLOW;
										else if(r<0.4f)       b.type = Tile.TILE_BUSH_ROSE;
										else if(r<0.5f)       b.type = Tile.TILE_BUSH_LAVENDER;
										else if(r<0.6f)       b.type = Tile.TILE_BUSH_HAZELNUT;
										else if(r<0.8f)       b.type = Tile.TILE_BUSH_RASPBERRYE;
										else                  b.type = Tile.TILE_BUSH_BLUEBERRY;
									}
								} else {
									if(r<=pd1) {
										r /= pd1;
											  if(r<0.05f)      b.type = Tile.TILE_TREE_FIR;
										else if(r<0.1f)       b.type = Tile.TILE_TREE_PINE;
										else if(r<0.15f)      b.type = Tile.TILE_TREE_BIRCH;
										else if(r<0.7f)       b.type = Tile.TILE_BUSH_BLUEBERRY;
										else                  b.type = Tile.TILE_BUSH_THORN;
									}
								}
							}
							if(b.type==Tile.TILE_TREE || b.type==Tile.TILE_BUSH)
								b.type = Tile.TILE_GRASS;
							if(b.type==Tile.TILE_GRASS) {
								r = biomeRandom.nextFloat();
								if(r<fd) {
									r /= fd;
									setFlowerType(x,y,1+((int)(r*7.0f))%7);
								}
							}
						} else {
							     if(b.surface>=-200 && b.env<beach) b.type = Tile.TILE_SAND;
							else if(b.surface>=-200 && b.env>clay) b.type = Tile.TILE_CLAY;
//							else if(b.surface>=-5 && b.slope<20 && b.climate>0.2f && b.env<-0.1f) b.type = Tile.TILE_MARSH;
							else if(b.surface>=-20 && ((b.climate>0.2f && b.env>0.1f) || r<0.05f) && b.isBelow(10)) b.type = Tile.TILE_REED;
							else if(b.surface>=-50 && b.surface<-10 && ((b.slope<15 && b.climate<-0.2f && b.env>0.1f) || (r>=0.05f && r<0.1f)) && b.isBelow(-5)) b.type = Tile.TILE_KELP;
							else if(b.type!=Tile.TILE_DIRT) b.type = Tile.TILE_DIRT;
						}
					}
				}
				progress.update(p+20);

				for(x=sx+2; x<ex-2; ++x)
					for(y=sy+2; y<ey-2; ++y) {
						b = biomes[x-sx][y-sy];
						if(b.type==Tile.TILE_MARSH) {
							for(i=0; i<20; ++i)
								if(b.area[i].type==Tile.TILE_SAND || b.area[i].type==Tile.TILE_DIRT)
									b.area[i].type = Tile.TILE_CLAY;
						}
					}

				for(x=sx+2; x<ex-2; ++x)
					for(y=sy+2; y<ey-2; ++y)
						setType(x,y,biomes[x-sx][y-sy].type);
			}

		MainWindow.log("Biome Generation completed in "+(System.currentTimeMillis()-startTime)+"ms.");
		MainWindow.log("minTemp="+minTemp+", maxTemp="+maxTemp);
		MainWindow.log("minEnv="+minEnv+", maxEnv="+maxEnv);
		MainWindow.log("minClimate="+minClimate+", maxClimate="+maxClimate);
	}

	/** Returns an area from the biomes of the surrounding 20 tiles:
	 * 1. up, right, down, left
	 * 2. 4 diagonal; starting up-left, moving clockwise
	 * 3. 4 two tiles away; starting up, moving clockwise
	 * 4. 8 two tiles away and one off; starting two up and one left, moving clockwise
	 * 
	 * Iterating through the array moves in circles from nearest to farthest.
	 */
	Biome[] getBiomeArea(int x,int y,int s) {
		s--;
		int t = s-1;
		Biome[] b = new Biome[20];
		b[ 0] =        y>0 ? biomes[x  ][y-1] : null;
		b[ 1] = x<s        ? biomes[x+1][y  ] : null;
		b[ 2] =        y<s ? biomes[x  ][y+1] : null;
		b[ 3] = x>0        ? biomes[x-1][y  ] : null;
		b[ 4] = x>0 && y>0 ? biomes[x-1][y-1] : null;
		b[ 5] = x<s && y>0 ? biomes[x+1][y-1] : null;
		b[ 6] = x<s && y<s ? biomes[x+1][y+1] : null;
		b[ 7] = x>0 && y<s ? biomes[x-1][y+1] : null;
		b[ 8] =        y>1 ? biomes[x  ][y-2] : null;
		b[ 9] = x<t        ? biomes[x+2][y  ] : null;
		b[10] =        y<t ? biomes[x  ][y+2] : null;
		b[11] = x>1        ? biomes[x-2][y  ] : null;
		b[12] = x>0 && y>1 ? biomes[x-1][y-2] : null;
		b[13] = x<s && y>1 ? biomes[x+1][y-2] : null;
		b[14] = x<t && y>0 ? biomes[x+2][y-1] : null;
		b[15] = x<t && y<s ? biomes[x+2][y+1] : null;
		b[16] = x<s && y<t ? biomes[x+1][y+2] : null;
		b[17] = x>0 && y<t ? biomes[x-1][y+2] : null;
		b[18] = x>1 && y<s ? biomes[x-2][y+1] : null;
		b[19] = x>1 && y>0 ? biomes[x-2][y-1] : null;
		return b;
	}

	Tile getType(int x,int y) {
		Tile type = typeMap[x][y];
		return type==null? Tile.TILE_ROCK : type;
	}

	short getFlowerType(int x,int y) {
		return flowerMap[x][y];
	}

	private Tile getType(Point p) {
		return getType(p.x,p.y);
	}

	private void setType(int x,int y,Tile newType) {
		typeMap[x][y] = newType;
	}

	private void setFlowerType(int x,int y,int newType) {
		flowerMap[x][y] = (short)newType;
	}

	private void setType(Point p,Tile newType) {
		setType(p.x,p.y,newType);
	}

	Tile getOreType(int x,int y) {
		Tile type = oreTypeMap[x][y];
		return type==null? Tile.TILE_CAVE_WALL : type;
	}

	private void setOreCount(int x,int y,int resourceCount) {
		oreResourceMap[x][y] = (short)resourceCount;
	}

	short getOreCount(int x,int y) {
		return oreResourceMap[x][y];
	}

	private void setOreType(int x,int y,Tile newType,int resourceCount) {
		if(!newType.isCave()) newType = Tile.TILE_CAVE_WALL;
		oreTypeMap[x][y] = newType;
		setOreCount(x,y,resourceCount);
	}

	boolean hasOres() { return hasOres; }

	private short getDirt(int x,int y) { return dirtMap[x][y]; }

	private void setDirt(int x,int y,short newDirt) {
		if(newDirt<0) newDirt = 0;
		if(newDirt==0) setType(x,y,Tile.TILE_ROCK);
		else {
			double h = getTileHeight(x,y);
			     if(h>=snowHeight) setType(x,y,Tile.TILE_SNOW);
			else if(h>=packedDirtHeight) setType(x,y,Tile.TILE_DIRT_PACKED);
			else if(h>=tundraHeight) setType(x,y,Tile.TILE_TUNDRA);
			else if(h>=waterHeight) setType(x,y,Tile.TILE_GRASS);
			else setType(x,y,Tile.TILE_DIRT);
		}
		dirtMap[x][y] = newDirt;
	}

	void addDirt(int x,int y,int count) {
		synchronized(this) {
			setDirt(x,y,(short)(getDirt(x,y)+count));
		}
	}

	private double getDirtHeight(int x,int y) {
		return getDirt(x,y)*singleDirt;
	}

	private double getTileHeight(int x,int y) {
		return heightMap.getHeight(x,y)+getDirtHeight(x,y);
	}

	short getSurfaceHeight(int x,int y) {
		return (short)((getTileHeight(x,y)-waterHeight)*heightMap.getMaxHeight());
	}

	short getRockHeight(int x,int y) {
		return (short)((heightMap.getHeight(x,y)-waterHeight)*heightMap.getMaxHeight());
	}

	int getMapHeight(int x,int y) {
		return (int)(getTileHeight(x,y)*heightMap.getMaxHeight());
	}

	private double getDifference(int x1,int y1,int x2,int y2) {
		return Math.abs(getTileHeight(x1,y1)-getTileHeight(x2,y2));
	}

	private double getDifference(Point p,Point p2) {
		return getDifference(p.x,p.y,p2.x,p2.y);
	}

	void setBiomeSeed(long newSeed) {
		biomeSeed = newSeed;
		biomeRandom = new Random(newSeed);
	}

	void setWaterHeight(int newHeight) {
		this.waterHeight = newHeight*singleDirt;
	}

	void setSnowHeight(int newHeight) {
		this.snowHeight = newHeight*singleDirt;
		setTundraHeight((newHeight*100)/70);
	}

	void setTundraHeight(int newHeight) {
		this.tundraHeight = newHeight*singleDirt;
		if(this.snowHeight>this.tundraHeight)
			this.packedDirtHeight = this.tundraHeight+(this.snowHeight-this.tundraHeight)*0.66d;
	}

	private Point findDropTile(int x,int y,double maxSlope,double maxDiagSlope) {
		ArrayList<Point> slopes = new ArrayList<Point>();
		double currentHeight = getTileHeight(x,y),thisHeight;
		int i,j,s = heightMap.getMapSize();
		for(i=x+1; i>=x-1; --i) {
			for(j=y+1; j>=y-1; --j) {
				if(i<0 || j<0 || i>=s || j>=s) continue;
				thisHeight = getTileHeight(i,j);
				if((i==0 && j!=0) || (i!=0 && j==0))
					if(thisHeight<=currentHeight-maxSlope)
						slopes.add(new Point(i,j));
				if(i!=0 && y!=0)
					if(thisHeight<=currentHeight-maxDiagSlope)
						slopes.add(new Point(i,j));
			}
		}
		if(slopes.size()>0) {
			int r = biomeRandom.nextInt(slopes.size());
			return findDropTile((int)slopes.get(r).getX(),(int)slopes.get(r).getY(),maxSlope,maxDiagSlope);
		} else {
			return new Point(x,y);
		}
	}

	private Point findErodeTile(int x,int y) {
		final int mapSize = heightMap.getMapSize();
		double currentHeight = getTileHeight(x,y);
//		MainWindow.log("findErodeTile(x: "+x+", y: "+y+", currentHeight: "+currentHeight+")");
		int cx = x,cy = y,lx = x,ly = y,rx = x,ry = y,n,i,j,q = 0;
		double lowest = currentHeight;
		double lowestRock = currentHeight;
		double h;
		Tile tile;
		while(true) {
			for(n=0; n<4; ++n) {
				i = cx+alignX[n];
				j = cy+alignY[n];
				if(i<0 || j<0 || i>=mapSize || j>=mapSize || (i==x && j==y)) continue;
				h = getTileHeight(i,j);
				if(h<lowest) {
					tile = typeMap[i][j];
					if(tile==null || tile==Tile.TILE_ROCK) {
						lowestRock = h;
						rx = i;
						ry = j;
					} else {
						lowest = h;
						lx = i;
						ly = j;
					}
				}
			}
//			MainWindow.log("findErodeTile(lowest: "+lowest+", lx: "+lx+", ly: "+ly+", lowestRock: "+lowestRock+", lrx: "+lrx+", lry: "+lry+", currentHeight: "+currentHeight+")");
			if(lowest<currentHeight) break;
			if(lowestRock>=currentHeight || q>2) return null;
			cx = rx;
			cy = ry;
			currentHeight = lowestRock;
			++q;
		}
//		MainWindow.log("findErodeTile(height: "+getTileHeight(x,y)+", x: "+x+", y: "+y+", lowest: "+lowest+", lx: "+lx+", ly: "+ly+", q: "+q+")");
		return new Point(lx,ly);
	}

	public static Tile getTileType(int r,int g,int b) {
		return colorMap.get(new Color(r,g,b));
	}

	public static Color getTileColor(Tile tile) {
		if(tile==Tile.TILE_GRASS) return new Color(54,101,3);
		for(Color c : colorMap.keySet())
			if(colorMap.get(c).id==tile.id) return c;
		return new Color(0,0,0);
	}

	private static void setupTileColorMap() {
		colorMap = new HashMap<Color,Tile>();
		colorMap.put(new Color(113,124,118), Tile.TILE_CLAY);
		colorMap.put(new Color( 75, 63, 47), Tile.TILE_DIRT);
		colorMap.put(new Color( 75, 63, 46), Tile.TILE_DIRT_PACKED);
		colorMap.put(new Color( 54,101,  3), Tile.TILE_GRASS);
		colorMap.put(new Color( 79, 74, 64), Tile.TILE_GRAVEL);
		colorMap.put(new Color( 54,101,  3), Tile.TILE_KELP);
		colorMap.put(new Color(215, 51, 30), Tile.TILE_LAVA);
		colorMap.put(new Color( 43,101, 72), Tile.TILE_MARSH);
		colorMap.put(new Color(106,142, 56), Tile.TILE_MOSS);
		colorMap.put(new Color( 54, 39, 32), Tile.TILE_PEAT);
		colorMap.put(new Color( 53,100,  2), Tile.TILE_REED);
		colorMap.put(new Color(114,110,107), Tile.TILE_ROCK);
		colorMap.put(new Color(160,147,109), Tile.TILE_SAND);
		colorMap.put(new Color(114,117, 67), Tile.TILE_STEPPE);
		colorMap.put(new Color( 18, 21, 40), Tile.TILE_TAR);
		colorMap.put(new Color(118,135,109), Tile.TILE_TUNDRA);
		colorMap.put(new Color( 41, 58,  1), Tile.TILE_TREE);
		colorMap.put(new Color( 41, 58,  4), Tile.TILE_TREE_APPLE);
		colorMap.put(new Color( 41, 58,  3), Tile.TILE_TREE_BIRCH);
		colorMap.put(new Color( 41, 58,  2), Tile.TILE_TREE_CEDAR);
		colorMap.put(new Color( 41, 58,  5), Tile.TILE_TREE_CHERRY);
		colorMap.put(new Color( 41, 58,  6), Tile.TILE_TREE_CHESTNUT);
		colorMap.put(new Color( 41, 58,  7), Tile.TILE_TREE_FIR);
		colorMap.put(new Color( 41, 58,  8), Tile.TILE_TREE_LEMON);
		colorMap.put(new Color( 41, 58,  9), Tile.TILE_TREE_LINDEN);
		colorMap.put(new Color( 41, 58, 10), Tile.TILE_TREE_MAPLE);
		colorMap.put(new Color( 41, 58, 11), Tile.TILE_TREE_OAK);
		colorMap.put(new Color( 41, 58, 12), Tile.TILE_TREE_OLIVE);
		colorMap.put(new Color( 41, 58, 13), Tile.TILE_TREE_PINE);
		colorMap.put(new Color( 41, 58, 14), Tile.TILE_TREE_WALNUT);
		colorMap.put(new Color( 41, 58, 15), Tile.TILE_TREE_WILLOW);
		colorMap.put(new Color( 41, 58, 16), Tile.TILE_TREE_ORANGE);
		colorMap.put(new Color( 58, 58,  0), Tile.TILE_BUSH);
		colorMap.put(new Color( 58, 58,  1), Tile.TILE_BUSH_CAMELLIA);
		colorMap.put(new Color( 58, 58,  2), Tile.TILE_BUSH_GRAPE);
		colorMap.put(new Color( 58, 58,  3), Tile.TILE_BUSH_LAVENDER);
		colorMap.put(new Color( 58, 58,  4), Tile.TILE_BUSH_OLEANDER);
		colorMap.put(new Color( 58, 58,  5), Tile.TILE_BUSH_ROSE);
		colorMap.put(new Color( 58, 58,  6), Tile.TILE_BUSH_THORN);
		colorMap.put(new Color( 58, 58,  7), Tile.TILE_BUSH_HAZELNUT);
		colorMap.put(new Color( 58, 58,  8), Tile.TILE_BUSH_RASPBERRYE);
		colorMap.put(new Color( 58, 58,  9), Tile.TILE_BUSH_BLUEBERRY);
		colorMap.put(new Color( 58, 58, 10), Tile.TILE_BUSH_LINGONBERRY);
		colorMap.put(new Color(155,151,148), Tile.TILE_CLIFF);
		colorMap.put(new Color(255,255,255), Tile.TILE_SNOW);
		colorMap.put(new Color(114,102, 80), Tile.TILE_PLANKS);
		colorMap.put(new Color( 99, 99, 99), Tile.TILE_STONE_SLABS);
		colorMap.put(new Color( 99, 99, 98), Tile.TILE_SLATE_SLABS);
		colorMap.put(new Color( 99, 99, 97), Tile.TILE_MARBLE_SLABS);
		colorMap.put(new Color( 92, 83, 73), Tile.TILE_COBBLESTONE);
		colorMap.put(new Color( 92, 83, 74), Tile.TILE_COBBLESTONE_ROUGH);
		colorMap.put(new Color( 92, 83, 75), Tile.TILE_COBBLESTONE_ROUND);
		colorMap.put(new Color( 71,  2, 51), Tile.TILE_MYCELIUM);

		for (int i = 1; i < 16; i++) {
			colorMap.put(new Color(220,250,50+i),Tile.TILE_GRASS);
		}
	}

}
