package net.spirangle.mapgen;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;

import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.GrassData.FlowerType;
import com.wurmonline.mesh.GrassData.GrowthStage;
import com.wurmonline.mesh.GrassData.GrowthTreeStage;
import com.wurmonline.mesh.Tiles.Tile;
import com.wurmonline.wurmapi.api.MapData;
import com.wurmonline.wurmapi.api.WurmAPI;

import net.spirangle.mapgen.util.Constants;
import net.spirangle.mapgen.util.ProgressHandler;
import net.spirangle.mapgen.util.StreamCapturer;

import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import java.awt.*;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.ArrayList;
import java.util.Random;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class MainWindow extends JFrame {

	private static final long serialVersionUID = -407206109473532425L;

	private static MainWindow window = null;

	private String file;

	private WurmAPI api;
	private HeightMap heightMap;
	private TileMap tileMap;
	private boolean apiClosed = true;
	private MapPanel mapPanel;
	private String mapName;
	private String actionsFileDirectory;
	private Constants.VIEW_TYPE defaultView = Constants.VIEW_TYPE.HEIGHT;
	private ProgressHandler progress;

	private JProgressBar progressBar;
	private JLabel lblMemory;
	private JPanel contentPane;
	private JButton btnQuickMap;
	private JButton btnRenderMap;
	private JTextArea textArea_Log;
	private JButton btnViewHeight;
	private JButton btnViewCave;
	private JButton btnViewTopo;
	private JButton btnViewMap;
	private JButton btnViewBiomes;
	private JLabel lblMapCoords;
	private JCheckBox chcekbox_showGrid;
	private JTextField textField_mapGridSize;
	private JButton btnViewErrors;
	private JTextArea textArea_Errors;
	private CardLayout cl_mainPanel;
	private JPanel mainPanel;

	private int mapSize;
	private int mapHeight;
	private double mapResolution;
	private double landscapeResolution;
	private long heightSeed;
	private long normalizeSeed;
	private long fractureSeed;
	private int heightMapIterations;
	private int heightMapMinEdge;
	private int heightMapBorderWeight;
	private int heightMapCompression;
	private int heightMapPeaks;
	private int erodeIterations;
	private int erodeMinSlope;
	private int erodeMaxSlope;
	private int erodeSediment;
	private int erodeFracture;
	private int dirtPerTile;
	private int maxDirtSlope;
	private int maxDiagSlope;
	private int maxDirtHeight;
	private int maxGrassHeight;
	private int waterHeight;
	private double cliffRatio;
	private int snowHeight;
	private int tundraHeight;
	private boolean moreLand;
	private boolean landSlide;
	private long biomeSeed;
	private double biomeResolution;
	private float biomeDesertTemp;
	private float biomeSteppeTemp;
	private float biomeTundraTemp;
	private float biomeGlacierTemp;
	private float biomeSubtropicalTemp;
	private float biomeMediterranTemp;
	private float biomeTemperateTemp;
	private int biomePlantDensity;
	private int biomeFlowerDensity;
	private int biomeBeaches;
	private int biomeClayPits;
	private double oreIron;
	private double oreGold;
	private double oreSilver;
	private double oreZinc;
	private double oreCopper;
	private double oreLead;
	private double oreTin;
	private double oreMarble;
	private double oreSlate;
	private double oreAddy;
	private double oreGlimmer;
	private double oreRocksalt;
	private double oreSandstone;
	private double rock;

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
			UIDefaults defaults = UIManager.getLookAndFeelDefaults();
			defaults.put("nimbusOrange",new Color(50,205,50));
		} catch (Throwable e) {
			e.printStackTrace();
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					window = new MainWindow(args.length>=1? args[0] : "mapgen");
					window.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}


	@SuppressWarnings({"unchecked","rawtypes"})
	public MainWindow(String file) {
		this.file = file;
		loadProperties();
		setTitle(Constants.WINDOW_TITLE+" - v"+Constants.VERSION);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100,100,1000,750);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5,5,5,5));
		setLocationRelativeTo(null);
		setContentPane(contentPane);
		btnQuickMap = new JButton("Quick Map");
		btnRenderMap = new JButton("Render Map");
		textArea_Log = new JTextArea("");
		textArea_Log.setEditable(false);
		textArea_Log.setLineWrap(true);
		textArea_Log.setWrapStyleWord(true);
		JScrollPane scrollPane = new JScrollPane(textArea_Log);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("");
		progressBar.setEnabled(true);
		progressBar.setValue(100);
		JPanel viewPanel = new JPanel();
		JPanel mapCoordsPanel = new JPanel();
		mainPanel = new JPanel();
		JPanel memoryPanel = new JPanel();
		GroupLayout gl_contentPane = new GroupLayout(contentPane);
		gl_contentPane.setHorizontalGroup(
				gl_contentPane.createParallelGroup(Alignment.TRAILING)
				.addGroup(gl_contentPane.createSequentialGroup()
						.addContainerGap()
						.addGroup(gl_contentPane.createParallelGroup(Alignment.TRAILING)
								.addComponent(mainPanel,GroupLayout.DEFAULT_SIZE,GroupLayout.DEFAULT_SIZE,Short.MAX_VALUE)
								.addComponent(progressBar,Alignment.LEADING,GroupLayout.DEFAULT_SIZE,641,Short.MAX_VALUE)
								.addComponent(mapCoordsPanel,GroupLayout.DEFAULT_SIZE,641,Short.MAX_VALUE)
								.addComponent(viewPanel,GroupLayout.DEFAULT_SIZE,641,Short.MAX_VALUE))
						.addPreferredGap(ComponentPlacement.RELATED)
						.addGroup(gl_contentPane.createParallelGroup(Alignment.LEADING,false)
								.addComponent(memoryPanel,GroupLayout.PREFERRED_SIZE,315,GroupLayout.PREFERRED_SIZE)
								.addComponent(scrollPane,GroupLayout.PREFERRED_SIZE,315,GroupLayout.PREFERRED_SIZE)
								.addComponent(btnQuickMap,GroupLayout.DEFAULT_SIZE,GroupLayout.DEFAULT_SIZE,Short.MAX_VALUE)
								.addComponent(btnRenderMap,GroupLayout.DEFAULT_SIZE,GroupLayout.DEFAULT_SIZE,Short.MAX_VALUE))
						.addContainerGap())
				);
		gl_contentPane.setVerticalGroup(
				gl_contentPane.createParallelGroup(Alignment.TRAILING)
				.addGroup(gl_contentPane.createSequentialGroup()
						.addComponent(btnQuickMap,GroupLayout.PREFERRED_SIZE,45,GroupLayout.PREFERRED_SIZE)
						.addComponent(btnRenderMap,GroupLayout.PREFERRED_SIZE,45,GroupLayout.PREFERRED_SIZE)
						.addGap(5)
						.addComponent(scrollPane,GroupLayout.DEFAULT_SIZE,603,Short.MAX_VALUE)
						.addGap(5)
						.addComponent(memoryPanel,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE))
				.addGroup(gl_contentPane.createSequentialGroup()
						.addComponent(progressBar,GroupLayout.PREFERRED_SIZE,19,GroupLayout.PREFERRED_SIZE)
						.addGap(5)
						.addComponent(mapCoordsPanel,GroupLayout.PREFERRED_SIZE,24,GroupLayout.PREFERRED_SIZE)
						.addGap(5)
						.addComponent(mainPanel,GroupLayout.DEFAULT_SIZE,603,Short.MAX_VALUE)
						.addGap(5)
						.addComponent(viewPanel,GroupLayout.PREFERRED_SIZE,GroupLayout.DEFAULT_SIZE,GroupLayout.PREFERRED_SIZE))
				);
		memoryPanel.setLayout(new GridLayout(0,2,5,0));

		JLabel lblMemoryUsage = new JLabel("Memory Usage:");
		lblMemoryUsage.setHorizontalAlignment(SwingConstants.RIGHT);
		memoryPanel.add(lblMemoryUsage);

		lblMemory = new JLabel("xx% of xxgb");
		lblMemory.setFont(new Font("SansSerif",Font.PLAIN,12));
		memoryPanel.add(lblMemory);
		lblMemory.setHorizontalAlignment(SwingConstants.CENTER);
		cl_mainPanel = new CardLayout(0,0);
		mainPanel.setLayout(cl_mainPanel);

		mapPanel = new MapPanel(this);
		mainPanel.add(mapPanel,"MAP");
		mapPanel.setGridSize(Constants.GRID_SIZE);

		JPanel errorPanel = new JPanel();
		mainPanel.add(errorPanel,"ERRORS");
		errorPanel.setLayout(new GridLayout(0,1,0,0));

		textArea_Errors = new JTextArea();
		errorPanel.add(new JScrollPane(textArea_Errors));
		textArea_Errors.setEditable(false);

		JPanel panel_25 = new JPanel();

		JLabel lblNewLabel_4 = new JLabel("Map Coords:");
		lblNewLabel_4.setHorizontalAlignment(SwingConstants.CENTER);

		lblMapCoords = new JLabel("");
		lblMapCoords.setHorizontalAlignment(SwingConstants.LEFT);

		JPanel panel_28 = new JPanel();
		panel_28.setLayout(new GridLayout(0,3,0,0));

		chcekbox_showGrid = new JCheckBox("Grid");
		panel_28.add(chcekbox_showGrid);
		chcekbox_showGrid.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				mapPanel.showGrid(chcekbox_showGrid.isSelected());
			}
		});
		chcekbox_showGrid.setHorizontalAlignment(SwingConstants.CENTER);

		JLabel lblSize = new JLabel("Size:");
		lblSize.setToolTipText("Grid cell count. Press enter to submit");
		lblSize.setHorizontalAlignment(SwingConstants.RIGHT);
		panel_28.add(lblSize);

		textField_mapGridSize = new JTextField("" + Constants.GRID_SIZE);
		textField_mapGridSize.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					mapPanel.setGridSize(Integer.parseInt(textField_mapGridSize.getText()));
				} catch (NumberFormatException ex) {
					JOptionPane.showMessageDialog(null,"Grid size must be an integer","Input Error",JOptionPane.WARNING_MESSAGE);
				}
			}
		});
		panel_28.add(textField_mapGridSize);
		textField_mapGridSize.setColumns(10);
		GroupLayout gl_mapCoordsPanel = new GroupLayout(mapCoordsPanel);
		gl_mapCoordsPanel.setHorizontalGroup(
				gl_mapCoordsPanel.createParallelGroup(Alignment.LEADING)
				.addGroup(Alignment.TRAILING,gl_mapCoordsPanel.createSequentialGroup()
						.addComponent(panel_25,GroupLayout.DEFAULT_SIZE,401,Short.MAX_VALUE)
						.addPreferredGap(ComponentPlacement.UNRELATED)
						.addComponent(panel_28,GroupLayout.PREFERRED_SIZE,179,GroupLayout.PREFERRED_SIZE)
						.addContainerGap())
				);
		gl_mapCoordsPanel.setVerticalGroup(
				gl_mapCoordsPanel.createParallelGroup(Alignment.LEADING)
				.addComponent(panel_25,GroupLayout.DEFAULT_SIZE,24,Short.MAX_VALUE)
				.addComponent(panel_28,GroupLayout.PREFERRED_SIZE,24,Short.MAX_VALUE)
				);
		GroupLayout gl_panel_25 = new GroupLayout(panel_25);
		gl_panel_25.setHorizontalGroup(
				gl_panel_25.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_panel_25.createSequentialGroup()
						.addComponent(lblNewLabel_4,GroupLayout.PREFERRED_SIZE,113,GroupLayout.PREFERRED_SIZE)
						.addPreferredGap(ComponentPlacement.RELATED)
						.addComponent(lblMapCoords,GroupLayout.DEFAULT_SIZE,270,Short.MAX_VALUE)
						.addContainerGap())
				);
		gl_panel_25.setVerticalGroup(
				gl_panel_25.createParallelGroup(Alignment.LEADING)
				.addComponent(lblNewLabel_4,GroupLayout.DEFAULT_SIZE,24,Short.MAX_VALUE)
				.addComponent(lblMapCoords,Alignment.TRAILING,GroupLayout.DEFAULT_SIZE,24,Short.MAX_VALUE)
				);
		panel_25.setLayout(gl_panel_25);
		mapCoordsPanel.setLayout(gl_mapCoordsPanel);

		btnViewMap = new JButton("View Map");
		viewPanel.add(btnViewMap);

		btnViewTopo = new JButton("View Topo");
		viewPanel.add(btnViewTopo);

		btnViewBiomes = new JButton("View Biomes");
		viewPanel.add(btnViewBiomes);

		btnViewCave = new JButton("View Cave");
		viewPanel.add(btnViewCave);

		btnViewHeight = new JButton("View Heightmap");
		viewPanel.add(btnViewHeight);

		btnViewErrors = new JButton("View Errors");
		btnViewErrors.setVisible(false);
		btnViewErrors.setBackground(new Color(255,51,51));
		btnViewErrors.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				cl_mainPanel.show(mainPanel,"ERRORS");
			}
		});
		viewPanel.add(btnViewErrors);
		contentPane.setLayout(gl_contentPane);
		init();
	}

	private void loadProperties() {
		MainWindow.log("Loading properties file "+file+".properties ...");
		Path path = Paths.get(file+".properties");
		if(!Files.exists(path)) {
			MainWindow.log("The properties file seems to be missing.");
		}
		InputStream stream = null;
		try {
			MainWindow.log("Opening the properties file.");
			stream = Files.newInputStream(path);
			Properties properties = new Properties();
			MainWindow.log("Reading from the properties file.");
			properties.load(stream);

			mapName               = properties.getProperty("mapName",Constants.MAP_NAME);

			mapSize               = Integer.parseInt(properties.getProperty("mapSize",Integer.toString(Constants.MAP_SIZE)));
			mapHeight             = Integer.parseInt(properties.getProperty("mapHeight",Integer.toString(Constants.MAP_HEIGHT)));
			mapResolution         = Double.parseDouble(properties.getProperty("mapResolution",Double.toString(Constants.MAP_RESOLUTION)));
			landscapeResolution   = Double.parseDouble(properties.getProperty("landscapeResolution",Double.toString(Constants.LANDSCAPE_RESOLUTION)));

			heightSeed            = Long.parseLong(properties.getProperty("heightSeed","0"));
			normalizeSeed         = Long.parseLong(properties.getProperty("normalizeSeed","0"));
			fractureSeed          = Long.parseLong(properties.getProperty("fractureSeed","0"));

			heightMapIterations   = Integer.parseInt(properties.getProperty("heightMapIterations",Integer.toString(Constants.HEIGHTMAP_ITERATIONS)));
			heightMapMinEdge      = Integer.parseInt(properties.getProperty("heightMapMinEdge",Integer.toString(Constants.HEIGHTMAP_MIN_EDGE)));
			heightMapBorderWeight = Integer.parseInt(properties.getProperty("heightMapBorderWeight",Integer.toString(Constants.HEIGHTMAP_BORDER_WEIGHT)));
			heightMapCompression  = Integer.parseInt(properties.getProperty("heightMapCompression",Integer.toString(Constants.HEIGHTMAP_COMPRESSION)));
			heightMapPeaks        = Integer.parseInt(properties.getProperty("heightMapPeaks",Integer.toString(Constants.HEIGHTMAP_PEAKS)));

			erodeIterations       = Integer.parseInt(properties.getProperty("erosionIterations",Integer.toString(Constants.EROSION_ITERATIONS)));
			erodeMinSlope         = Integer.parseInt(properties.getProperty("erosionMinSlope",Integer.toString(Constants.EROSION_MIN_SLOPE)));
			erodeMaxSlope         = Integer.parseInt(properties.getProperty("erosionMaxSlope",Integer.toString(Constants.EROSION_MAX_SLOPE)));
			erodeSediment         = Integer.parseInt(properties.getProperty("erosionMaxSediment",Integer.toString(Constants.EROSION_MAX_SEDIMENT)));
			erodeFracture         = Integer.parseInt(properties.getProperty("erosionFracture",Integer.toString(Constants.EROSION_FRACTURE)));

			dirtPerTile           = Integer.parseInt(properties.getProperty("dirtDropCount",Integer.toString(Constants.DIRT_DROP_COUNT)));
			maxDirtSlope          = Integer.parseInt(properties.getProperty("maxDirtSlope",Integer.toString(Constants.MAX_DIRT_SLOPE)));
			maxDiagSlope          = Integer.parseInt(properties.getProperty("maxDirtDiagSlope",Integer.toString(Constants.MAX_DIRT_DIAG_SLOPE)));
			waterHeight           = Integer.parseInt(properties.getProperty("waterHeight",Integer.toString(Constants.WATER_HEIGHT)));
			cliffRatio            = Double.parseDouble(properties.getProperty("cliffRatio",Double.toString(Constants.CLIFF_RATIO)));
			maxDirtHeight         = Integer.parseInt(properties.getProperty("dirtHeight",Integer.toString(Constants.DIRT_HEIGHT)));
			snowHeight            = Integer.parseInt(properties.getProperty("snowHeight",Integer.toString(Constants.SNOW_HEIGHT)));
			tundraHeight          = Integer.parseInt(properties.getProperty("tundraHeight",Integer.toString(Constants.TUNDRA_HEIGHT)));
			moreLand              = Boolean.parseBoolean(properties.getProperty("moreLand",Boolean.toString(Constants.MORE_LAND)));
			landSlide             = Boolean.parseBoolean(properties.getProperty("landSlide",Boolean.toString(Constants.LAND_SLIDE)));

			biomeSeed             = Long.parseLong(properties.getProperty("biomeSeed",Long.toString(Constants.BIOME_SEED)));
			biomeResolution       = Double.parseDouble(properties.getProperty("biomeResolution",Double.toString(Constants.BIOME_RESOLUTION)));
			biomeDesertTemp       = Float.parseFloat(properties.getProperty("biomeDesertTemp",Float.toString(Constants.BIOME_DESERT_TEMP)));
			biomeSteppeTemp       = Float.parseFloat(properties.getProperty("biomeSteppeTemp",Float.toString(Constants.BIOME_STEPPE_TEMP)));
			biomeTundraTemp       = Float.parseFloat(properties.getProperty("biomeTundraTemp",Float.toString(Constants.BIOME_TUNDRA_TEMP)));
			biomeGlacierTemp      = Float.parseFloat(properties.getProperty("biomeGlacierTemp",Float.toString(Constants.BIOME_GLACIER_TEMP)));
			biomeSubtropicalTemp  = Float.parseFloat(properties.getProperty("biomeSubtropicalTemp",Float.toString(Constants.BIOME_SUBTROPICAL_TEMP)));
			biomeMediterranTemp   = Float.parseFloat(properties.getProperty("biomeMediterranTemp",Float.toString(Constants.BIOME_MEDITERRAN_TEMP)));
			biomeTemperateTemp    = Float.parseFloat(properties.getProperty("biomeTemperateTemp",Float.toString(Constants.BIOME_TEMPERATE_TEMP)));
			biomePlantDensity     = Integer.parseInt(properties.getProperty("biomePlantDensity",Integer.toString(Constants.BIOME_PLANT_DENSITY)));
			biomeFlowerDensity    = Integer.parseInt(properties.getProperty("biomeFlowerDensity",Integer.toString(Constants.BIOME_FLOWER_DENSITY)));
			biomeBeaches          = Integer.parseInt(properties.getProperty("biomeBeaches",Integer.toString(Constants.BIOME_BEACHES)));
			biomeClayPits         = Integer.parseInt(properties.getProperty("biomeClayPits",Integer.toString(Constants.BIOME_CLAY_PITS)));

			oreIron               = Double.parseDouble(properties.getProperty("oreIron",Double.toString(Constants.ORE_IRON)));
			oreGold               = Double.parseDouble(properties.getProperty("oreGold",Double.toString(Constants.ORE_GOLD)));
			oreSilver             = Double.parseDouble(properties.getProperty("oreSilver",Double.toString(Constants.ORE_SILVER)));
			oreZinc               = Double.parseDouble(properties.getProperty("oreZinc",Double.toString(Constants.ORE_ZINC)));
			oreCopper             = Double.parseDouble(properties.getProperty("oreCopper",Double.toString(Constants.ORE_COPPER)));
			oreLead               = Double.parseDouble(properties.getProperty("oreLead",Double.toString(Constants.ORE_LEAD)));
			oreTin                = Double.parseDouble(properties.getProperty("oreTin",Double.toString(Constants.ORE_TIN)));
			oreAddy               = Double.parseDouble(properties.getProperty("oreAddy",Double.toString(Constants.ORE_ADDY)));
			oreGlimmer            = Double.parseDouble(properties.getProperty("oreGlimmer",Double.toString(Constants.ORE_GLIMMER)));
			oreMarble             = Double.parseDouble(properties.getProperty("oreMarble",Double.toString(Constants.ORE_MARBLE)));
			oreSlate              = Double.parseDouble(properties.getProperty("oreSlate",Double.toString(Constants.ORE_SLATE)));
			oreSandstone          = Double.parseDouble(properties.getProperty("oreSandstone",Double.toString(Constants.ORE_SANDSTONE)));
			oreRocksalt           = Double.parseDouble(properties.getProperty("oreRocksalt",Double.toString(Constants.ORE_ROCKSALT)));

			if(heightSeed==0L)	{
				if("".equals(mapName)) {
					heightSeed = System.currentTimeMillis();
					mapName = ""+heightSeed;
				} else {
					heightSeed = (long)mapName.hashCode();
				}
			} else if("".equals(mapName)) {
				mapName = ""+heightSeed;
			}
			if(normalizeSeed==0L) normalizeSeed = heightSeed;
			if(fractureSeed==0L) fractureSeed = heightSeed;
			if(biomeSeed==0L) biomeSeed = heightSeed;

			MainWindow.log("Configuration loaded.");
		} catch(Exception e) {
			MainWindow.log("Error while loading properties file: "+e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if(stream!=null) stream.close();
			} catch(Exception e) {
				MainWindow.log("Properties file not closed, possible file lock: "+e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void init() {
		setupButtonActions();
		setRockTotal();
		updateMapCoords(0,0,false);
		progress = new ProgressHandler(progressBar,lblMemory);
		progress.update(100);
		System.setErr(new PrintStream(new StreamCapturer(System.err,this)));
	}

	private void setupButtonActions() {
		btnViewMap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				cl_mainPanel.show(mainPanel,"MAP");
				if(!actionReady()) return;
				new Thread() {
					@Override
					public void run() {
						actionViewMap();
					}
				}.start();
			}
		});
		btnViewTopo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cl_mainPanel.show(mainPanel,"MAP");
				if(!actionReady()) return;
				new Thread() {
					@Override
					public void run() {
						actionViewTopo();
					}
				}.start();
			}
		});
		btnViewBiomes.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cl_mainPanel.show(mainPanel,"MAP");
				if(!actionReady()) return;
				new Thread() {
					@Override
					public void run() {
						actionViewBiomes();
					}
				}.start();
			}
		});
		btnViewCave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cl_mainPanel.show(mainPanel,"MAP");
				if(!actionReady()) return;
				new Thread() {
					@Override
					public void run() {
						actionViewCave();
					}
				}.start();
			}
		});
		btnViewHeight.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cl_mainPanel.show(mainPanel,"MAP");
				if(!actionReady()) return;
				new Thread() {
					@Override
					public void run() {
						actionViewHeightmap();
					}
				}.start();
			}
		});
		btnQuickMap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(!actionReady()) return;
				new Thread() {
					@Override
					public void run() {
						actionQuickMap();
					}
				}.start();
			}
		});
		btnRenderMap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(!actionReady()) return;
				new Thread() {
					@Override
					public void run() {
						actionRenderMap();
					}
				}.start();
			}
		});
	}

	private void startLoading(String task) {
		progress.update(0,task);
	}

	private void stopLoading() {
		progress.update(100,"");
	}

	boolean actionReady() {
		return progressBar.getValue()==100;
	}

	void actionQuickMap() {
		MainWindow.log("Generating quick map...");
		loadProperties();
		actionQuickRender();
		defaultView = Constants.VIEW_TYPE.ISO;
		updateMapView();
		MainWindow.log("done\n");
	}

	void actionRenderMap() {
		MainWindow.log("Generating map...");
		if(actionGenerateHeightmap()) {
			defaultView = Constants.VIEW_TYPE.HEIGHT;
			updateMapView();

			actionErodeHeightmap();
			updateMapView();

			actionDropDirt();
			defaultView = Constants.VIEW_TYPE.ISO;
			updateMapView();

			actionErodeDirt();
			updateMapView();

			actionGenerateOres();

			actionGenerateBiomes();
			defaultView = Constants.VIEW_TYPE.ISO;
			updateMapView();

			actionSaveImages();
			actionSaveMap();
		}
		MainWindow.log("done\n");
	}

	void actionQuickRender() {
		MainWindow.log("Quick rendering map...");
		startLoading("Quick Rendering Map");
		try {
			api = null;
			mapPanel.setMapSize(mapSize);
			heightMap = new HeightMap(mapSize,mapResolution,landscapeResolution,heightMapIterations,heightMapMinEdge,heightMapBorderWeight,mapHeight,
			                          heightMapCompression,heightMapPeaks,moreLand);
			heightMap.setSeeds(heightSeed,normalizeSeed,fractureSeed);
			heightMap.generateHeights(progress);
			heightMap.exportHeightImage(mapName,"heightmap.png");

			tileMap = new TileMap(heightMap);
			tileMap.setBiomeSeed(biomeSeed);
			tileMap.setWaterHeight(waterHeight);
			tileMap.setSnowHeight(snowHeight);
			tileMap.setTundraHeight(tundraHeight);
			tileMap.dropDirt(2,maxDirtSlope,maxDiagSlope,maxDirtHeight,cliffRatio,landSlide,progress);

		} catch(NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null,"Error parsing number "+nfe.getMessage().toLowerCase(),"Error Generating HeightMap",JOptionPane.ERROR_MESSAGE);
		} finally {
			stopLoading();
		}
	}

	boolean actionGenerateHeightmap() {
		MainWindow.log("Generating height map...");
		startLoading("Generating Height Map");
		try {
			api = null;
			mapPanel.setMapSize(mapSize);
			heightMap = new HeightMap(mapSize,mapResolution,landscapeResolution,heightMapIterations,heightMapMinEdge,heightMapBorderWeight,mapHeight,
			                          heightMapCompression,heightMapPeaks,moreLand);
			heightMap.setSeeds(heightSeed,normalizeSeed,fractureSeed);
			if(!heightMap.importHeightImage(mapName,"heightmap.png")) {
				heightMap.generateHeights(progress);
				heightMap.exportHeightImage(mapName,"heightmap.png");
			}
		} catch(NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null,"Error parsing number "+nfe.getMessage().toLowerCase(),"Error Generating HeightMap",JOptionPane.ERROR_MESSAGE);
		} finally {
			stopLoading();
		}
		return true;
	}

	void actionErodeHeightmap() {
		MainWindow.log("Eroding height map...");
		if(heightMap==null) {
			JOptionPane.showMessageDialog(null,"HeightMap does not exist","Error Eroding HeightMap",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Eroding Height Map");
		try {
			heightMap.erode(erodeIterations,erodeMinSlope,erodeMaxSlope,erodeSediment,progress);
			heightMap.fracture(erodeFracture,progress);
		} catch(NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null,"Error parsing number "+nfe.getMessage().toLowerCase(),"Error Eroding HeightMap",JOptionPane.ERROR_MESSAGE);
		} finally {
			stopLoading();
		}
	}

	void actionDropDirt() {
		MainWindow.log("Dropping dirt...");
		if(heightMap==null) {
			JOptionPane.showMessageDialog(null,"HeightMap does not exist","Error Dropping Dirt",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Dropping Dirt");
		try {
			MainWindow.log("Water: "+waterHeight);
			tileMap = new TileMap(heightMap);
			tileMap.setBiomeSeed(biomeSeed);
			tileMap.setWaterHeight(waterHeight);
			tileMap.setSnowHeight(snowHeight);
			tileMap.setTundraHeight(tundraHeight);
			tileMap.dropDirt(dirtPerTile,maxDirtSlope,maxDiagSlope,maxDirtHeight,cliffRatio,landSlide,progress);
		} catch(NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null,"Error parsing number "+nfe.getMessage().toLowerCase(),"Error Dropping Dirt",JOptionPane.ERROR_MESSAGE);
		} finally {
			stopLoading();
		}
	}

	void actionErodeDirt() {
		MainWindow.log("Eroding dirt...");
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist","Error Eroding TileMap",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Eroding Tile Map");
		try {
			tileMap.erode(erodeIterations,erodeMinSlope,erodeMaxSlope,erodeSediment,progress);
		} catch(NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null,"Error parsing number "+nfe.getMessage().toLowerCase(),"Error Eroding TileMap",JOptionPane.ERROR_MESSAGE);
		} finally {
			stopLoading();
		}
	}

	void actionGenerateOres() {
		MainWindow.log("Generating ores...");
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Resetting Biomes",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Generating Ores");
		try {
			setRockTotal();
			if(rock<0.0 || rock>100.0) {
				JOptionPane.showMessageDialog(null,"Ore values out of range","Error Generating Ore",JOptionPane.ERROR_MESSAGE);
				return;
			}
			double[] rates = { rock,oreIron,oreGold,oreSilver,oreZinc,oreCopper,oreLead,oreTin,oreAddy,oreGlimmer,oreMarble,oreSlate,oreSandstone,oreRocksalt };
			tileMap.generateOres(rates,progress);
		} catch(NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null,"Error parsing number "+nfe.getMessage().toLowerCase(),"Error Generating Ores",JOptionPane.ERROR_MESSAGE);
		} finally {
			stopLoading();
		}
	}

	void actionGenerateBiomes() {
		MainWindow.log("Generating biomes...");
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Resetting Biomes",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Generating Biomes");
		try {
			tileMap.importBiomeImage(mapName,"biomemap.png");
			tileMap.generateBiomes(biomeResolution,biomeDesertTemp,biomeSteppeTemp,biomeTundraTemp,biomeGlacierTemp,
			                       biomeSubtropicalTemp,biomeMediterranTemp,biomeTemperateTemp,biomePlantDensity,biomeFlowerDensity,
			                       biomeBeaches,biomeClayPits,progress);
		} catch(NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null,"Error parsing number "+nfe.getMessage().toLowerCase(),"Error Generating Ores",JOptionPane.ERROR_MESSAGE);
		} finally {
			stopLoading();
		}
	}

	void actionViewMap() {
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Showing Map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Loading");
		try {
			defaultView = Constants.VIEW_TYPE.ISO;
			updateMapView();
		} finally {
			stopLoading();
		}
	}

	void actionViewTopo() {
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Showing Map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Loading");
		try {
			defaultView = Constants.VIEW_TYPE.TOPO;
			updateMapView();
		} finally {
			stopLoading();
		}
	}

	void actionViewBiomes() {
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Showing Map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Loading");
		try {
			defaultView = Constants.VIEW_TYPE.BIOMES;
			updateMapView();
		} finally {
			stopLoading();
		}
	}

	void actionViewCave() {
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Showing Map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		if(!tileMap.hasOres()) {
			JOptionPane.showMessageDialog(null,"No Cave Map - Generate Ores first","Error Showing Map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Loading");
		try {
			defaultView = Constants.VIEW_TYPE.CAVE;
			updateMapView();
		} finally {
			stopLoading();
		}
	}

	void actionViewHeightmap() {
		if(heightMap==null) {
			JOptionPane.showMessageDialog(null,"HeightMap does not exist","Error Showing Map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Loading");
		try {
			defaultView = Constants.VIEW_TYPE.HEIGHT;
			updateMapView();
		} finally {
			stopLoading();
		}
	}

	void actionSaveImages() {
		MainWindow.log("Saving images...");
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Saving Images",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Saving Images");
		try {
			updateAPIMap();
			MapData map = getAPI().getMapData();
			ImageIO.write(map.createMapDump(),"png",new File("./maps/"+mapName+"/map.png"));
			ImageIO.write(map.createTopographicDump(true,(short)250),"png",new File("./maps/"+mapName+"/topography.png"));
			ImageIO.write(map.createCaveDump(true),"png",new File("./maps/"+mapName+"/cave.png"));
//			heightMap.exportHeightImage(mapName,"heightmap.png");
			saveBiomesImage();
		} catch(IOException ex) {
			ex.printStackTrace();
		} finally {
			stopLoading();
		}
	}

	private void saveBiomesImage() {
		try {
			BufferedImage bufferedImage = getBiomeImage();
			File imageFile = new File("./maps/"+mapName+"/" + "biomes.png");
			if(!imageFile.exists()) imageFile.mkdirs();
			ImageIO.write(bufferedImage,"png",imageFile);
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}

	void actionSaveMap() {
		MainWindow.log("Saving map...");
		if(tileMap==null) {
			JOptionPane.showMessageDialog(null,"TileMap does not exist - Add Dirt first","Error Saving Map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		startLoading("Saving Map");
		try {
			updateAPIMap();
			getAPI().getMapData().saveChanges();
		} finally {
			stopLoading();
		}
	}

	private WurmAPI getAPI() {
		if(apiClosed) api = null;
		if(api==null)
			try {
				api = WurmAPI.create("./maps/"+mapName+"/",(int)(Math.log(heightMap.getMapSize())/Math.log(2)));
				apiClosed = false;
			} catch(IOException e) {
				e.printStackTrace();
			}
		return api;
	}


	private void updateMapView() {
		if(defaultView==Constants.VIEW_TYPE.HEIGHT) {
			startLoading("Loading View");
			Graphics g = mapPanel.getMapImage().getGraphics();
			float h;
			for(int i=0; i<heightMap.getMapSize(); ++i) {
				progress.update((int)((float)i/heightMap.getMapSize()*98f));
				for(int j=0; j<heightMap.getMapSize(); ++j) {
					h = (float)heightMap.getHeight(i,j);
					h = Math.min(1.0f,Math.max(0.0f,h));
					g.setColor(new Color(h,h,h));
					g.fillRect(i,j,1,1);
				}
			}
		} else {
			updateAPIMap();
			if(defaultView==Constants.VIEW_TYPE.TOPO)
				mapPanel.setMapImage(getAPI().getMapData().createTopographicDump(true,(short)250));
			else if(defaultView==Constants.VIEW_TYPE.CAVE)
				mapPanel.setMapImage(getAPI().getMapData().createCaveDump(true));
			else if(defaultView==Constants.VIEW_TYPE.ISO)
				mapPanel.setMapImage(getAPI().getMapData().createMapDump());
			else if(defaultView==Constants.VIEW_TYPE.BIOMES)
				mapPanel.setMapImage(getBiomeImage());
		}
		mapPanel.updateScale();
		mapPanel.checkBounds();
		mapPanel.repaint();
		stopLoading();
	}

	private void updateAPIMap() {
		startLoading("Updating Map");
		MapData map = getAPI().getMapData();
		Random treeRand = new Random(System.currentTimeMillis());
		try {
			for(int i=0; i<heightMap.getMapSize(); ++i) {
				progress.update((int)((float)i/heightMap.getMapSize()*100f/3));
				for(int j=0; j<heightMap.getMapSize(); ++j) {
					map.setSurfaceHeight(i,j,tileMap.getSurfaceHeight(i,j));
					map.setRockHeight(i,j,tileMap.getRockHeight(i,j));
					if(tileMap.hasOres()) {
						map.setCaveTile(i,j,tileMap.getOreType(i,j),tileMap.getOreCount(i,j));
					}
					map.setSurfaceTile(i,j,Tile.TILE_ROCK);
				}
			}
			for(int i=0; i<heightMap.getMapSize(); ++i) {
				progress.update((int)((float)i/heightMap.getMapSize()*100f/3)+33);
				for(int j=0; j<heightMap.getMapSize(); ++j) {
					if(tileMap.getType(i,j)!=Tile.TILE_ROCK && !tileMap.getType(i,j).isTree() && !tileMap.getType(i,j).isBush()) {
						for(int x=i-1; x<=i+1; ++x) {
							for(int y=j-1; y<=j+1; ++y) {
								if(x>0 && y>0 && x<heightMap.getMapSize() && y<heightMap.getMapSize()) {
									map.setSurfaceTile(x,y,tileMap.getType(i,j));
									map.setGrass(x,y,GrowthStage.MEDIUM,FlowerType.fromInt(tileMap.getFlowerType(x,y)));
								}
							}
						}
					}
				}
			}
			for(int i=0; i<heightMap.getMapSize(); ++i) {
				progress.update((int)((float)i/heightMap.getMapSize()*100f/3)+66);
				for(int j=0; j<heightMap.getMapSize(); ++j) {
					if(tileMap.getType(i,j).isTree()) {
						map.setTree(i,j,tileMap.getType(i,j).getTreeType((byte) 0),FoliageAge.values()[treeRand.nextInt(FoliageAge.values().length)],GrowthTreeStage.MEDIUM);
					} else if(tileMap.getType(i,j).isBush()) {
						map.setBush(i,j,tileMap.getType(i,j).getBushType((byte) 0),FoliageAge.values()[treeRand.nextInt(FoliageAge.values().length)],GrowthTreeStage.MEDIUM);
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			stopLoading();
			System.gc();//TODO
		}
	}

	private class TextFileView extends FileFilter {
		public boolean accept(File f) {
			if(f.isDirectory()) {
				return true;
			}
			String extension = getExtension(f);
			if(extension!=null)
				if(extension.equals("txt")) return true;
			return false;
		}

		private String getExtension(File f) {
			String ext = null;
			String s = f.getName();
			int i = s.lastIndexOf('.');
			if(i>0 && i<s.length()-1)
				ext = s.substring(i+1).toLowerCase();
			return ext;
		}

		@Override
		public String getDescription() {
			return "Biome Files (.txt)";
		}
	}

	private class ImageFileView extends FileFilter {
		public boolean accept(File f) {
			if(f.isDirectory()) {
				return true;
			}
			String extension = getExtension(f);
			if(extension!=null)
				if(extension.equals("png")) return true;
			return false;
		}

		private String getExtension(File f) {
			String ext = null;
			String s = f.getName();
			int i = s.lastIndexOf('.');
			if(i>0 &&  i<s.length()-1)
				ext = s.substring(i+1).toLowerCase();
			return ext;
		}

		@Override
		public String getDescription() {
			return "Image File (.png)";
		}
	}

	private void setRockTotal() {
		double[] rates = { oreIron,oreGold,oreSilver,oreZinc,oreCopper,oreLead,oreTin,oreAddy,oreGlimmer,oreMarble,oreSlate,oreSandstone,oreRocksalt };
		double total = 0.0;
		for(int i=0; i<rates.length; ++i) total += rates[i];
		rock = 100.0-total;
	}

	private BufferedImage getBiomeImage() {
		mapSize = heightMap.getMapSize();
		BufferedImage bufferedImage = new BufferedImage(mapSize,mapSize,BufferedImage.TYPE_INT_RGB);
		WritableRaster wr = (WritableRaster) bufferedImage.getRaster();
		int[] array = new int[mapSize*mapSize*3];
		for(int x=0; x<mapSize; ++x) {
			for(int y=0; y<mapSize; ++y) {
				final Tile tile = api.getMapData().getSurfaceTile(x,y);
				final Color color;
				if(tile!=null) {
					if(tile==Tile.TILE_GRASS && tileMap.getFlowerType(x,y)!=0) {
						color = new Color(220,250,tileMap.getFlowerType(x,y)+50);
					} else {
						color = TileMap.getTileColor(tile);
					}
				} else {
					color = TileMap.getTileColor(Tile.TILE_DIRT);
				}
				array[(x+y*mapSize)*3+0] = color.getRed();
				array[(x+y*mapSize)*3+1] = color.getGreen();
				array[(x+y*mapSize)*3+2] = color.getBlue();
			}
		}
		wr.setPixels(0,0,mapSize,mapSize,array);
		bufferedImage.setData(wr);
		return bufferedImage;
	}

	public void updateMapCoords (int x,int y,boolean show) {
		if(show && tileMap!=null) {
			int height = tileMap.getMapHeight(x,mapPanel.getMapSize()-y);
			lblMapCoords.setText("Tile ("+x+","+y+"),Player ("+(x*4)+","+(y*4)+"),Height ("+height+")");
		} else {
			lblMapCoords.setText("Right click to place a marker");
		}
	}

	public void submitError(String err) {
		textArea_Errors.append(err);
		btnViewErrors.setVisible(true);
	}

	public static void log(String s) {
		System.out.println(s);
		if(window!=null) window.textArea_Log.append(s+"\n");
	}
}
