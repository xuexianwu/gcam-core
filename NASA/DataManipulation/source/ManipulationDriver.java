
/*
 * This software, which is provided in confidence, was prepared by employees
        of Pacific Northwest National Laboratory operated by Battelle Memorial
        Institute. Battelle has certain unperfected rights in the software 
        which should not be copied or otherwise disseminated outside your 
        organization without the express written authorization from Battelle. All rights in
        the software are reserved by Battelle.  Battelle makes no warranty,
        express or implied, and assumes no liability or responsibility for the
        use of this software.
 */
/*!
 * \file ManipulationDriver.java
 * \ingroup DataManipulation
 * \brief Driver class for this program, runs based on supplied XML files.
 *
 *  Gets a list of commands, a list of data, and a definition of region hierarchy.
 * Based on these XML files creates a list of subRegions, builds a list of
 * superRegions from those. Then runs through the users commands and outputs the
 * results to the defined medium.
 *
 * \author Vincent Nibali
 * \date $Date$
 * \version $Revision$
 */

package source;

/*
 * TreeMap(name)->Region->TreeMap(variable)->TreeMap(time)->Matrix(values)
 */

import java.io.*;
import java.util.*;

import org.jdom.*;
import org.jdom.input.*;

import Jama.*;


/**
 * Driver class for this program, runs based on supplied XML files. Gets a list of commands,
 * a list of data, and a definition of region hierarchy.
 * Based on these XML files creates a list of subRegions, builds a list of
 * superRegions from those. Then runs through the users commands and outputs the
 * results to the defined medium.
 * 
 * @author Vincent Nibali
 * @version 1.0
 */
public class ManipulationDriver
{
  
  public TreeMap regionList; //master list of all regions, regular and super
  public TreeMap variableList; //master list of all variables, reference or data
  public TreeMap dataAvgAdd; //a mapping of data names to wether they are averaged or added on aggregation
  public TreeMap dataRef; //a listing of the references for each data type for which one was supplied
  public TreeMap dataUnits; //a listing of the units which each variables values represent
  private String dSource; //the fielname sub regions and data are read from
  private String rSource; //the filename the region hierarchy is defined in
  private String cSource; //the filename which the users commands come from
  private Document dDocument; //document of data to create regions
  private Document rDocument; //document of region hierarchy
  private Document cDocument; //document of user commands
  private double resolution; //the resolution which data is stored at
  private int numAtomicRegions; //the number of lowest level regions (i dont think i even use this anymore)
  
//*****************************************************************************
//*****************Class Constructors******************************************
//*****************************************************************************
  /**
   * Default Constuctor. Uses 'out.xml' for data input, 'regionDef.xml' for region hierarchy,
   * and 'commands.xml' for the users desired commands.
   */
  public ManipulationDriver()
  {
    dSource = "out.xml";
    rSource = "regionDef.xml";
    cSource = "commands.xml";
    regionList = new TreeMap();
    variableList = new TreeMap();
    dataAvgAdd = new TreeMap();
    dataRef = new TreeMap();
    dataUnits = new TreeMap();
  }
  /**
   * Standard constructor which takes all input files as paramaters.
   * @param d Source for data to be read in.
   * @param r Source for the hierarchy of regions to be read.
   * @param c Source of the users desired run commands.
   */
  public ManipulationDriver(String d, String r, String c)
  {
    dSource = d;
    rSource = r;
    cSource = c;
    regionList = new TreeMap();
    variableList = new TreeMap();
    dataAvgAdd = new TreeMap();
    dataRef = new TreeMap();
    dataUnits = new TreeMap();
  }
  
//*****************************************************************************
//***************Begin Functions Proper****************************************
//*****************************************************************************
  /**
   * Wrapper function which runs all needed functions in the correct order for the user.
   */
  public void runAll()
  {
    /*
    try{
    System.out.println("Begin. waiting...");
    System.in.read();
    System.in.read();
    System.out.println("...going");
    */
    makeStreams();
    buildRegionData();
    buildRegionHierarchy();
    inputParser();
    /*
    } catch(IOException e){}
    */
  }
  /**
   * Reads in a list of regions from the supplied xml file. Regions are filled with data
   * as a {@link TreeMap} of variables, each with a TreeMap of times, then a matrix of double
   * values. Each region contains a data matrix called 'weight' which is the portion of
   * each cell which lies in that particular region (used for boarder cells).
   *
   */
  public void buildRegionData()
  {
    Element root = dDocument.getRootElement();
    List regionChildren = root.getChildren("region");
    Element currRegion;
    
    resolution = Double.parseDouble(root.getAttributeValue("res"));
    
    //getting the information for each field, such as reference and units
    currRegion = root.getChild("variableInfo");
    setFieldInfo(currRegion);
    
    numAtomicRegions = regionChildren.size();
    //MAIN XML READ LOOP FOR READING REGION DATA
    for(int i = 0; i < numAtomicRegions; i++)
    {
      addRegion((Element)regionChildren.get(i));
    }
    //END MAIN XML LOOP FOR REGION BUILD
    
  }
  /**
   * Reads definitions of super regions from an XML file and adds them to the region list.
   * Hierarchy of regions is defined in the file by what level the region resides on.
   * Levels are dependant on the dependencies of the region. Level 1 only contains
   * subRegions, level 2 can contain subRegions and level 1, and so on.
   *
   */
  public void buildRegionHierarchy()
  {
    Element root = rDocument.getRootElement();
    int numLevels = Integer.parseInt(root.getAttributeValue("numLevels"));
    List superChildren[] = new List[numLevels];
    for(int i = 0; i < numLevels; i++)
    {
      superChildren[i] = new ArrayList();
    }
    List holdChildren = root.getChildren("superRegion");
    for(int i = 0; i < holdChildren.size(); i++)
    { //this is annoying but i think it will make creating region defs easier for the user
      //what im doing is splitting children into lists based on their lvl
      Element hold = (Element)holdChildren.get(i);
      superChildren[(Integer.parseInt(hold.getAttributeValue("level"))-1)].add(hold);
    }
    //BEGIN MAIN LOOP FOR READING SUPER REGIONS
    for(int i = 0; i < numLevels; i++)
    {
      for(int j = 0; j < superChildren[i].size(); j++)
      {
        addSuperRegion((Element)superChildren[i].get(j));
      }
    }
    //END MAIN LOOP FOR SUPER REGION CREATION
  }
  /**
   * Parses commands from the user's input and runs the appropriate manipulator
   * functions. Commands include defining variables (as scalars or regions), manipulating
   * the data in those variables, and printing variables to the screen (or some other output).
   *
   */
  public void inputParser()
  {
    List coms;
    Element currCom;
    Element root = cDocument.getRootElement();
    coms = root.getChildren();
    
    for(int i = 0; i < coms.size(); i++)
    {
      currCom = (Element)coms.get(i);
      if(currCom.getName().equals("variable"))
      {
        if(currCom.getAttributeValue("type").equals("data"))
        {
          newDataVariableCommand(currCom);
        } else  if(currCom.getAttributeValue("type").equals("reference"))
        {
          newReferenceVariableCommand(currCom);
        } else  if(currCom.getAttributeValue("type").equals("group"))
        {
          newGroupVariableCommand(currCom);
        } else
        {
          System.out.println("Unknown variable type -> "+currCom.getAttributeValue("type"));
        }
      } else if(currCom.getName().equals("aggregateVariables"))
      {
        aggregateVariablesCommand(currCom);
      } else if(currCom.getName().equals("add"))
      {
        addCommand(currCom);
      } else if(currCom.getName().equals("subtract"))
      {
        subCommand(currCom);
      } else if(currCom.getName().equals("addScalar"))
      {
        addScalarCommand(currCom);
      } else if(currCom.getName().equals("multiply"))
      {
        multiplyCommand(currCom);
      } else if(currCom.getName().equals("divide"))
      {
        divideCommand(currCom);
      } else if(currCom.getName().equals("multiplyScalar"))
      {
        multiplyScalarCommand(currCom);
      } else if(currCom.getName().equals("divideScalar"))
      {
        divideScalarCommand(currCom);
      } else if(currCom.getName().equals("parseGreaterThan"))
      {
        parseGreaterThanCommand(currCom);
      } else if(currCom.getName().equals("parseLessThan"))
      {
        parseLessThanCommand(currCom);
      } else if(currCom.getName().equals("countGreaterThan"))
      {
        countGreaterThanCommand(currCom);
      } else if(currCom.getName().equals("countLessThan"))
      {
        countLessThanCommand(currCom);
      } else if(currCom.getName().equals("countElements"))
      {
        countElementsCommand(currCom);
      } else if(currCom.getName().equals("largestValue"))
      {
        largestValueCommand(currCom);
      } else if(currCom.getName().equals("smallestValue"))
      {
        smallestValueCommand(currCom);
      } else if(currCom.getName().equals("aggregateValues"))
      {
        aggregateValuesCommand(currCom);
      } else if(currCom.getName().equals("sumValues"))
      {
        sumValuesCommand(currCom);
      } else if(currCom.getName().equals("avgOverRegion"))
      {
        avgOverRegionCommand(currCom);
      } else if(currCom.getName().equals("avgOverRegionByArea"))
      {
        avgOverRegionByAreaCommand(currCom);
      } else if(currCom.getName().equals("avgVariables"))
      {
        avgVariablesCommand(currCom);
      } else if(currCom.getName().equals("avgVariablesOverRegion"))
      {
        avgVariablesOverRegionCommand(currCom);
      } else if(currCom.getName().equals("avgVariablesOverRegionByArea"))
      {
        avgVariablesOverRegionByAreaCommand(currCom);
      } else if(currCom.getName().equals("weightValues"))
      {
        weightValuesCommand(currCom);
      } else if(currCom.getName().equals("extractSubRegion"))
      {
        extractSubRegionCommand(currCom);
      } else if(currCom.getName().equals("print"))
      {
        printCommand(currCom);
      } else if(currCom.getName().equals("printVerbose"))
      {
        printVerboseCommand(currCom);
      } else if(currCom.getName().equals("plot"))
      {
        plotCommand(currCom);
      } else if(currCom.getName().equals("createDataSet"))
      {
       createDataSetCommand(currCom);
      } else if(currCom.getName().equals("comment"))
      {
        commentCommand(currCom);
      } else if(currCom.getName().equals("setReference"))
      {
        setReferenceCommand(currCom);
      } else if(currCom.getName().equals("setUnits"))
      {
        setUnitsCommand(currCom);
      } else
      {
        System.out.println("Unknown input command -> "+currCom.getName());
      }
    }
  }
  
//*****************************************************************************
//******************Input Command Runners**************************************
//*****************************************************************************
  /**
   * Creates a new variable which holds only data. Builds according to the
   * contents of the passed XML node.
   * @param command XML command the variable will be based on.
   */
  private void newDataVariableCommand(Element command)
  {
    Element currInfo;
    List dataChildren;
    String newName = command.getAttributeValue("name");
    Variable toAdd;
    int hY, hX;
    
    currInfo = command.getChild("dimension");
    int dim = Integer.parseInt(currInfo.getAttributeValue("value"));
    if(dim == 1)
    {
      currInfo = command.getChild("size");
      hX = Integer.parseInt(currInfo.getAttributeValue("x"));
      double[] toAddData = new double[hX];
      for(int i = 0; i < hX; i++)
      {
        toAddData[i] = Double.NaN;
      }
      
      dataChildren = command.getChildren("data");
      for(int i = 0; i < dataChildren.size(); i++)
      {
        currInfo = (Element)dataChildren.get(i);
        hX = Integer.parseInt(currInfo.getAttributeValue("x"));
        toAddData[hX] = Double.parseDouble(currInfo.getAttributeValue("value"));
      }
      
      toAdd = new DataVariable(newName, toAddData);
    } else if(dim == 2)
    {
      currInfo = command.getChild("size");
      hY = Integer.parseInt(currInfo.getAttributeValue("y"));
      hX = Integer.parseInt(currInfo.getAttributeValue("x"));
      double[][] toAddData = new double[hY][hX];
      for(int i = 0; i < hY; i++)
      {
        for(int k = 0; k < hX; k++)
        {
          toAddData[i][k] = Double.NaN;
        }
      }
      
      dataChildren = command.getChildren("data");
      for(int i = 0; i < dataChildren.size(); i++)
      {
        currInfo = (Element)dataChildren.get(i);
        hY = Integer.parseInt(currInfo.getAttributeValue("y"));
        hX = Integer.parseInt(currInfo.getAttributeValue("x"));
        toAddData[hY][hX] = Double.parseDouble(currInfo.getAttributeValue("value"));
      }
      
      toAdd = new DataVariable(newName, toAddData);
    } else //dim == 0, no size element
    {
      currInfo = command.getChild("data");
      double toAddData = Double.parseDouble(currInfo.getAttributeValue("value"));
      toAdd = new DataVariable(newName, toAddData);
    }
    
    variableList.put(newName, toAdd);
  }
  
  /**
   * Creates a new variable which holds a regions worth of data. Builds
   * according to the contents of the passed XML node.
   * @param command XML command the variable will be based on.
   */
  private void newReferenceVariableCommand(Element command)
  {
    ReferenceVariable toAdd;
    Element currInfo;
    String newName = command.getAttributeValue("name");
    Region ref;
    String var, time;
    boolean avg;
    
    currInfo = command.getChild("region");
    if(regionList.containsKey(currInfo.getAttributeValue("value")))
    {
      ref = (Region)regionList.get(currInfo.getAttributeValue("value"));
      currInfo = command.getChild("field");
      var = currInfo.getAttributeValue("value");
      currInfo = command.getChild("time");
      time = currInfo.getAttributeValue("value");
      avg = ((Boolean)dataAvgAdd.get(var)).booleanValue();
      
      //creating the reference variable (fills with data also)
      toAdd = new ReferenceVariable(newName, ref, var, time, avg);
      
      //setting optional information
      if(dataRef.containsKey(var))
      {
        toAdd.reference = (String)dataRef.get(var);
      }
      if(dataUnits.containsKey(var))
      {
        toAdd.units = (String)dataUnits.get(var);
      }
      currInfo = command.getChild("comment");
      if(currInfo != null)
      {
       toAdd.comment = currInfo.getAttributeValue("value"); 
      }
      
      variableList.put(newName, toAdd);
    } else
    {
      System.out.println("Region: "+currInfo.getAttributeValue("value")+" does not exist.");
    }
    
  }
  
  /**
   * Creates a new variable which holds a group of other variables. Child
   * variables can be of any type. Defined uses are for holding all time
   * occurances of a field in a region, or holding all first level sub-
   * regions of a variable.
   * @param command XML command the variable will be filled based on.
   */
  private void newGroupVariableCommand(Element command)
  {
    GroupVariable toAdd;
    String newName = command.getAttributeValue("name");
    Element currInfo;
    
    toAdd = new GroupVariable(newName);
    
    currInfo = command.getChild("fill");
    String fillType = currInfo.getAttributeValue("value");
    
    if(fillType.equals("time"))
    { //filled by each time entry for a certain region and field
      toAdd.isRegion = false;
      toAdd.isTime = true;
      
      fillGroupByTime(toAdd, command.getChild("members"));
    } else if(fillType.equals("subregions"))
    { //some sortof subregions will fill group
      toAdd.isRegion = true;
      toAdd.isTime = false;
      
      currInfo = command.getChild("members");
      if(currInfo.getAttributeValue("variable") != null)
      { //given a variable, extract all first level subregions from it
        fillGroupByExtraction(toAdd, currInfo);
      } else
      { //generate all first level subregion variables
        fillGroupByChildren(toAdd, currInfo);
      }
    } else
    { //filled by explicitly listed variables
      toAdd.isRegion = false;
      toAdd.isTime = false;
      
      fillGroupByExplicit(toAdd, command.getChild("members"));
    }
    
    if(toAdd != null)
    {
      currInfo = command.getChild("comment");
      if(currInfo != null)
      {
       toAdd.comment = currInfo.getAttributeValue("value"); 
      }
      variableList.put(newName, toAdd);
    } else
    { //returned a null pointer when we tried to fill because of a problem getting data
      System.out.println("Error creating group variable -> "+newName);
    }
  }
  
  /**
   * Creates a new variable as an aggregation of a list of variables.
   * Can be used only for reference variables as aggregation is undefined
   * for data variables.
   * @param command XML command listing variable to aggregate.
   */
  private void aggregateVariablesCommand(Element command)
  {
    ReferenceVariable VDest = null;
    Element currInfo;
    List args;
    ReferenceVariable[] varData;
    int DWcount = 0; //number of data wrappers (sub regions) in all variables being added to this one
    
    currInfo = command.getChild("target");
    String VDname = currInfo.getAttributeValue("name");
    args = command.getChildren("argument");
    varData = new ReferenceVariable[args.size()];
    for(int i = 0; i < args.size(); i++)
    {
      currInfo = (Element)args.get(i);
      varData[i] = (ReferenceVariable)variableList.get(currInfo.getAttributeValue("name"));
      DWcount += varData[i].data.length;
    }
    //creating new datavariable to hold result
    //we are adding region stuff, need to store answer in region stuff
    VDest = new ReferenceVariable(VDname, varData[0]);
    VDest.data = new Wrapper[DWcount];
    VDest.region = null;
    
    DWcount = 0;
    for(int i = 0; i < varData.length; i++)
    {//updating aggregated regions bounds
      if(varData[i].y < VDest.y)
      {
        VDest.h += (VDest.y-varData[i].y);
        VDest.y = varData[i].y;
      }
      if((varData[i].y+varData[i].h)>(VDest.y+VDest.h))
      {
        VDest.h = ((varData[i].y+varData[i].h)-VDest.y);
      }
      if(varData[i].x<VDest.x)
      {
        VDest.w += (VDest.x-varData[i].x);
        VDest.x = varData[i].x;
      }
      if((varData[i].x+varData[i].w)>(VDest.x+VDest.w))
      {
        VDest.w = ((varData[i].x+varData[i].w)-VDest.x);
      }
      //adding member variables subregions to this variables subregion list
      for(int k = 0; k < varData[i].data.length; k++)
      {
        VDest.data[DWcount] = varData[i].data[k];
        DWcount++;
      }
    }
    
    //filling this new aggregated variables weight with NaN's
    VDest.weight = new double[(int)(VDest.h/VDest.res)][(int)(VDest.w/VDest.res)];
    for(int iY = 0; iY < VDest.weight.length; iY++)
    {
      for(int iX = 0; iX < VDest.weight[iY].length; iX++)
      {
        VDest.weight[iY][iX] = Double.NaN;
      }
    }
    //filling weight with actual weights for locatiosn which exist in it
    double[][] holdW;
    int offsetY, offsetX;
    for(int i = 0; i < varData.length; i++)
    {
      holdW = varData[i].weight;
      offsetY = (int)(((VDest.y+VDest.h)-(varData[i].y+varData[i].h))/VDest.res);
      offsetX = (int)((varData[i].x-VDest.x)/VDest.res);
      for(int iY = 0; iY < holdW.length; iY++)
      {
        for(int iX = 0; iX < holdW[iY].length; iX++)
        {
          if(Double.isNaN(VDest.weight[(offsetY+iY)][(offsetX+iX)]))
          {
            VDest.weight[(offsetY+iY)][(offsetX+iX)] = (holdW[iY][iX]);
          }
        }
      }
    }
    
    variableList.put(VDname, VDest);
  }
  
  /**
   * Adds the corresponding positions in the two passed variables. Requires that the
   * variables are of the same shape.
   * @param command XML node defining the operation.
   */
  private void addCommand(Element command)
  {
    Variable VD, V1, V2;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VD = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));

      VD = V1.getShape(VDname);
      variableList.put(VDname, VD);
    }
    
    VD.setData(addVar(V1.getData(), V2.getData()));
  }
  
  /**
   * Subtracts the corresponding positions in the two passed variables. Requires that the
   * variables are of the same shape.
   * @param command XML node defining the operation.
   */
  private void subCommand(Element command)
  {
    Variable VD, V1, V2;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VD = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      VD = V1.getShape(VDname);
      variableList.put(VDname, VD);
    }
    
    VD.setData(subtractVar(V1.getData(), V2.getData()));
  }

  /**
   * Adds a scalar to every position in the sent variable. There is no subtract
   * scalar command as negatives can be sent to this one.
   * @param command XML node defining the operation.
   */
  private void addScalarCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    double change;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scalar");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        change = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        change = holdChange.getData()[0].data[0][0];
      }
      
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scalar");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        change = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        change = holdChange.getData()[0].data[0][0];
      }
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(addVar(VSource.getData(), change));
  }
  
  /**
   * Multiplys the corresponding positions in the two passed variables. Requires
   * that the variables are of the same shape.
   * @param command XML node defining the operation.
   */
  private void multiplyCommand(Element command)
  {
    Variable VD, V1, V2;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VD = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      VD = V1.getShape(VDname);
      variableList.put(VDname, VD);
    }
    
    VD.setData(multiplyVar(V1.getData(), V2.getData()));
  }
  
  /**
   * Divides the corresponding positions in the two passed variables. Requires
   * that the variables are of the same shape.
   * @param command XML node defining the operation.
   */
  private void divideCommand(Element command)
  {
    Variable VD, V1, V2;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VD = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      infoList = command.getChildren("argument");
      currInfo = (Element)infoList.get(0);
      V1 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = (Element)infoList.get(1);
      V2 = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      VD = V1.getShape(VDname);
      variableList.put(VDname, VD);
    }
    
    VD.setData(divideVar(V1.getData(), V2.getData()));
  }
  
  /**
   * Multiplys a scalar to every position in the sent variable.
   * @param command XML node defining the operation.
   */
  private void multiplyScalarCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    double change;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scalar");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        change = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        change = holdChange.getData()[0].data[0][0];
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scalar");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        change = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        change = holdChange.getData()[0].data[0][0];
      }
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(multiplyVar(VSource.getData(), change));
  }
  
  /**
   * Divides every position in the sent variable by the scalar.
   * @param command XML node defining the operation.
   */
  private void divideScalarCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    double change;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scalar");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        change = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        change = holdChange.getData()[0].data[0][0];
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scalar");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        change = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        change = holdChange.getData()[0].data[0][0];
      }
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(divideVar(VSource.getData(), change));
  }
  
  /**
   * Allows only values greater than either a scalar or corresponding
   * positions in another variable to remain. The rest are set to 0.
   * @param command XML node defining the operation.
   */
  private void parseGreaterThanCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Variable VMask = null;
    Element currInfo;
    double limit = 0;
    boolean reg = false;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    if(reg)
    {
      VDest.setData(greaterThanRegion(VSource.getData(), VMask.getData()));
    } else
    {
      VDest.setData(greaterThan(VSource.getData(), limit));
    }
  }
  
  /**
   * Allows only values less than either a scalar or corresponding
   * positions in another variable to remain. The rest are set to 0.
   * @param command XML node defining the operation.
   */
  private void parseLessThanCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Variable VMask = null;
    Element currInfo;
    double limit = 0;
    boolean reg = false;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    if(reg)
    {
      VDest.setData(lessThanRegion(VSource.getData(), VMask.getData()));
    } else
    {
      VDest.setData(lessThan(VSource.getData(), limit));
    }
  }
  
  /**
   * Counts the occurance of elements which are greater in value than either
   * the passed scalar or the passed variable.
   * @param command XML node defining the operation.
   */
  private void countGreaterThanCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Variable VMask = null;
    Element currInfo;
    double limit = 0;
    boolean reg = false;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    if(reg)
    {
      VDest.setData(countGreaterThanRegion(VSource.getData(), VMask.getData()));
    } else
    {
      VDest.setData(countGreaterThan(VSource.getData(), limit));
    }
  }
  
  /**
   * Counts the occurance of elements which are less in value than either
   * the passed scalar or the passed variable.
   * @param command XML node defining the operation.
   */
  private void countLessThanCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Variable VMask = null;
    Element currInfo;
    double limit = 0;
    boolean reg = false;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("limit");
      if(currInfo != null)
      { //this command gives a limit
        limit = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { // this command gives a corresponding region
        reg = true;
        currInfo = command.getChild("mask");
        VMask = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      }
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    if(reg)
    {
      VDest.setData(countLessThanRegion(VSource.getData(), VMask.getData()));
    } else
    {
      VDest.setData(countLessThan(VSource.getData(), limit));
    }
  }
  
  /**
   * Counts the number of valid elements in the passed variable. Valid elements
   * are ones which are not defined as outside the mask of a region.
   * @param command XML node defining the operation.
   */
  private void countElementsCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Element currInfo;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }

    VDest.setData(countElements(VSource.getData()));
  }
  
  /**
   * Determines whether the data contained in this variable is added or averaged
   * on aggregation, then performs the correct operation with all valid
   * elements. Valid elements
   * are ones which are not defined as outside the mask of a region.
   * @param command XML node defining the operation.
   */
  private void aggregateValuesCommand(Element command)
  {
    Variable VDest;
    ReferenceVariable VSource;
    Element currInfo;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (ReferenceVariable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (ReferenceVariable)variableList.get(currInfo.getAttributeValue("name"));
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    
    if((VSource).avg)
    { //dont need to weight values
      VDest.setData(avgOverRegion(VSource.getData(), VSource.weight, VSource.x, VSource.y, VSource.h));
    } else
    {
      VDest.setData(sumValues(VSource.getData()));
    }
  }
  
  /**
   * Sums the value of all valid elements in a variable. Valid elements
   * are ones which are not defined as outside the mask of a region.
   * @param command XML node defining the operation.
   */
  private void sumValuesCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Element currInfo;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    
    if((VSource.isReference())&&(((ReferenceVariable)VSource).avg))
    { //dont need to weight values
      VDest.setData(sumValues(((ReferenceVariable)VSource).getData(), ((ReferenceVariable)VSource).weight, ((ReferenceVariable)VSource).x, ((ReferenceVariable)VSource).y, ((ReferenceVariable)VSource).h));
    } else
    {
      VDest.setData(sumValues(VSource.getData()));
    }
  }
   
  /**
   * Gets the largest single value in the passed variable.
   * @param command XML node defining the operation.
   */
  private void largestValueCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Element currInfo;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(largestValue(VSource.getData()));
  }
  
  /**
   * Gets the smallest single value in the passed variable.
   * @param command XML node defining the operation.
   */
  private void smallestValueCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Element currInfo;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(smallestValue(VSource.getData()));
  }
  
  /**
   * Gets the average of all valid elements in a varaible. Valid elements
   * are ones which are not defined as outside the mask of a region. Values
   * which are only paritally in this region are weighted to account for
   * this fact, if that have not already been weighted.
   * @param command XML node defining the operation.
   */
  private void avgOverRegionCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Element currInfo;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      //creating new datavariable to hold result
      VDest = new DataVariable();//a line of commentedness!
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    
    if((VSource.isReference())&&(((ReferenceVariable)VSource).avg))
    { //dont need to weight values
      VDest.setData(avgOverRegion(VSource.getData(), ((ReferenceVariable)VSource).weight, ((ReferenceVariable)VSource).x, ((ReferenceVariable)VSource).y, ((ReferenceVariable)VSource).h));
    } else
    {
      VDest.setData(avgOverRegion(VSource.getData()));
    }
  }
  
  /**
   * Gets the average of all valid elements in a varaible with respect to
   * their proportion of area. Area changes based on latitude. Valid elements
   * are ones which are not defined as outside the mask of a region. Values
   * which are only paritally in this region are weighted to account for
   * this fact, if that have not already been weighted.
   * @param command XML node defining the operation.
   */
  private void avgOverRegionByAreaCommand(Element command)
  {
    Variable VDest;
    ReferenceVariable VSource;
    Element currInfo;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (ReferenceVariable)variableList.get(currInfo.getAttributeValue("name"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (ReferenceVariable)variableList.get(currInfo.getAttributeValue("name"));
      //creating new datavariable to hold result
      VDest = new DataVariable();
      VDest.name = VDname;
      variableList.put(VDname, VDest);
    }
    //this will only be called on refrencevariables... obviously
    if((VSource).avg)
    {
      VDest.setData(avgOverRegionByArea(VSource.getData(), VSource.weight, VSource.x, VSource.y, VSource.w, VSource.h));
    } else
    { //no weighting if not averaged
      VDest.setData(avgOverRegionByArea(VSource.getData(), VSource.x, VSource.y, VSource.w, VSource.h));
    }
    
  }
  
  /**
   * Gets the average at each location of all the variable values which 
   * occur at that location in their respective variable. Valid elements
   * are ones which are not defined as outside the mask of a region.
   * @param command XML node defining the operation.
   */
  private void avgVariablesCommand(Element command)
  {
    Variable VDest = null;
    Variable VSource = null;
    Element currInfo;
    List args;
    Wrapper[][] sendData;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      args = command.getChildren("argument");
      sendData = new Wrapper[args.size()][];
      for(int i = 0; i < args.size(); i++)
      {
        currInfo = (Element)args.get(i);
        VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        sendData[i] = VSource.getData();
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      args = command.getChildren("argument");
      sendData = new Wrapper[args.size()][];
      for(int i = 0; i < args.size(); i++)
      {
        currInfo = (Element)args.get(i);
        VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        sendData[i] = VSource.getData();
      }
      //creating new datavariable to hold result
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(avgVariables(sendData));
  }
  
  /**
   * Gets the average for a region of a list of overlapping variables.
   * Valid elements are ones which are not defined as outside the mask of
   * a region.
   * @param command XML node defining the operation.
   */
  private void avgVariablesOverRegionCommand(Element command)
  {
    Variable VDest = null;
    Variable VSource = null;
    Element currInfo;
    List args;
    Wrapper[] toSend;
    Wrapper[][] sendData;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      args = command.getChildren("argument");
      sendData = new Wrapper[args.size()][];
      for(int i = 0; i < args.size(); i++)
      {
        currInfo = (Element)args.get(i);
        VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        sendData[i] = VSource.getData();
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      args = command.getChildren("argument");
      sendData = new Wrapper[args.size()][];
      for(int i = 0; i < args.size(); i++)
      {
        currInfo = (Element)args.get(i);
        VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        sendData[i] = VSource.getData();
      }
      //creating new datavariable to hold result
      VDest = new DataVariable(VDname);
      variableList.put(VDname, VDest);
    }
    
    if((VSource.isReference())&&(((ReferenceVariable)VSource).avg))
    { //dont need to weight values
      toSend = avgVariables(sendData);
      VDest.setData(avgOverRegion(toSend, ((ReferenceVariable)VSource).weight, ((ReferenceVariable)VSource).x, ((ReferenceVariable)VSource).y, ((ReferenceVariable)VSource).h));
    } else
    {
      toSend = avgVariables(sendData);
      VDest.setData(avgOverRegion(toSend));
    }
  }
  
  /**
   * Gets the average for a region of a list of overlapping variables
   * accounting for area. Area changes based on latitude.
   * Valid elements are ones which are not defined as outside the mask of
   * a region.
   * @param command XML node defining the operation.
   */
  private void avgVariablesOverRegionByAreaCommand(Element command)
  {
    Variable VDest = null;
    Variable VSource = null;
    Element currInfo;
    List args;
    Wrapper[] toSend;
    Wrapper[][] sendData;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      args = command.getChildren("argument");
      sendData = new Wrapper[args.size()][];
      for(int i = 0; i < args.size(); i++)
      {
        currInfo = (Element)args.get(i);
        VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        sendData[i] = VSource.getData();
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      args = command.getChildren("argument");
      sendData = new Wrapper[args.size()][];
      for(int i = 0; i < args.size(); i++)
      {
        currInfo = (Element)args.get(i);
        VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        sendData[i] = VSource.getData();
      }
      //creating new datavariable to hold result
      VDest = new DataVariable(VDname);
      variableList.put(VDname, VDest);
    }
    
    if((VSource.isReference())&&(((ReferenceVariable)VSource).avg))
    { //need to weight values
      toSend = avgVariables(sendData);
      VDest.setData(avgOverRegionByArea(toSend,  ((ReferenceVariable)VSource).weight, ((ReferenceVariable)VSource).x, ((ReferenceVariable)VSource).y, ((ReferenceVariable)VSource).w, ((ReferenceVariable)VSource).h));
    } else
    {
      toSend = avgVariables(sendData);
      VDest.setData(avgOverRegionByArea(toSend, ((ReferenceVariable)VSource).x, ((ReferenceVariable)VSource).y, ((ReferenceVariable)VSource).w, ((ReferenceVariable)VSource).h));
    }
  }
  
  /**
   * Weights each valid value in the variable by another mask variable's
   * coresponding positions based one value limits and weight limits.
   * Valid elements are ones which are not defined as outside the mask of
   * a region.
   * @param command XML node defining the operation.
   */
  private void weightValuesCommand(Element command)
  {
    Variable VDest = null;
    Variable VSource = null;
    Variable VScale = null;
    List infoList;
    Element currInfo;
    Element minMax;
    double minWeight, maxWeight;
    double minVal, maxVal;
    
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scale");
      VScale = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      //this makes sure a scalar variable can be used anywhere a typed scalar can
      minMax = currInfo.getChild("minimum");
      if(minMax.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        minVal = Double.parseDouble(minMax.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(minMax.getAttributeValue("name"));
        minVal = holdChange.getData()[0].data[0][0];
      }
      
      minMax = currInfo.getChild("maximum");
      if(minMax.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        maxVal = Double.parseDouble(minMax.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(minMax.getAttributeValue("name"));
        maxVal = holdChange.getData()[0].data[0][0];
      }
      
      currInfo = command.getChild("minimumWeight");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        minWeight = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        minWeight = holdChange.getData()[0].data[0][0];
      }
      
      currInfo = command.getChild("maximumWeight");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        maxWeight = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        maxWeight = holdChange.getData()[0].data[0][0];
      }
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("argument");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("scale");
      VScale = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      //this makes sure a scalar variable can be used anywhere a typed scalar can
      minMax = currInfo.getChild("minimum");
      if(minMax.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        minVal = Double.parseDouble(minMax.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(minMax.getAttributeValue("name"));
        minVal = holdChange.getData()[0].data[0][0];
      }
      
      minMax = currInfo.getChild("maximum");
      if(minMax.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        maxVal = Double.parseDouble(minMax.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(minMax.getAttributeValue("name"));
        maxVal = holdChange.getData()[0].data[0][0];
      }
      
      currInfo = command.getChild("minimumWeight");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        minWeight = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        minWeight = holdChange.getData()[0].data[0][0];
      }
      
      currInfo = command.getChild("maximumWeight");
      if(currInfo.getAttribute("value") != null)
      { //this is just a number, so go ahead and read it
        maxWeight = Double.parseDouble(currInfo.getAttributeValue("value"));
      } else
      { //this is a scalar variable, get the value out of it
        Variable holdChange = (Variable)variableList.get(currInfo.getAttributeValue("name"));
        maxWeight = holdChange.getData()[0].data[0][0];
      }
      
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(weightValues(VSource.getData(), VScale.getData(), minVal, maxVal, minWeight, maxWeight));
  }
  
  /**
   * Extracts a subregion of the specified reference variable. The subregion
   * is defined as a region which is the whole or part of the shape of the
   * passed reference variable. Extracted region can be on any level of the
   * sent region.
   * @param command XML node defining the operation.
   */
  private void extractSubRegionCommand(Element command)
  {
    Variable VDest;
    Variable VSource;
    Region VShape;
    double change;
    List infoList;
    Element currInfo;
    currInfo = command.getChild("target");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      VDest = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("source");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("shape");
      VShape = (Region)regionList.get(currInfo.getAttributeValue("value"));
    }
    else
    {
      String VDname = currInfo.getAttributeValue("name");
      currInfo = command.getChild("source");
      VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
      currInfo = command.getChild("shape");
      VShape = (Region)regionList.get(currInfo.getAttributeValue("value"));
      
      VDest = VSource.getShape(VDname);
      variableList.put(VDname, VDest);
    }
    
    VDest.setData(VShape.extractRegion((ReferenceVariable)VSource));
  }
  
  /**
   * Give the user access to a contained Child varialbe in the passed
   * Group variable. Must know the contained name of the variable to be
   * extracted. For a time group name will be the string time respresentation
   * of the data. For subregion group name will be the name of the region
   * mapped by said child variable. For explicit groups child name is the
   * name of the variable used in group creation.
   * @param command XML node defining the operation.
   */
  private void getChildVariable(Element command)
  {
    Variable VDest;
    Variable VSource;
    Variable Vchild;
    String cName;
    double change;
    List infoList;
    Element currInfo;
    
    currInfo = command.getChild("target");
    String VDname = currInfo.getAttributeValue("name");
    currInfo = command.getChild("source");
    VSource = (Variable)variableList.get(currInfo.getAttributeValue("name"));
    currInfo = command.getChild("child");
    cName = currInfo.getAttributeValue("value");
    
    if(VSource.isGroup())
    {
      if(((GroupVariable)VSource).data.containsKey(cName))
      {
        Vchild = (Variable)((GroupVariable)VSource).data.get(cName);
        
        VDest = Vchild.getCopy(VDname);
        variableList.put(VDname, VDest);
      } else
      {
        System.out.println("Source variable: "+VSource.name+" does not contain child variable: "+cName);
      }
    } else
    {
      System.out.println("Source variable: "+VSource.name+" is not a Group variable.");
    }
  }
  
  /**
   * Prints only the actual data elements of the passed variable to the screen.
   * @param command XML node defining the operation.
   */
  private void printCommand(Element command)
  {
    Variable toPrint;
    
    if(variableList.containsKey(command.getAttributeValue("variable")))
    {
      toPrint = (Variable)variableList.get(command.getAttributeValue("variable"));
      toPrint.printStandard();
    } else
    { //this variable did not exist
      System.out.println("Variable: "+command.getAttributeValue("variable")+" is undefined.");
    }
  }
  
  /**
   * Prints the passed variable to the screen along with additional qualifying
   * information such as name of variable, region, bounding rectangles.
   * @param command XML node defining the operation.
   */
  private void printVerboseCommand(Element command)
  {
    Variable toPrint;
    
    if(variableList.containsKey(command.getAttributeValue("variable")))
    {
      toPrint = (Variable)variableList.get(command.getAttributeValue("variable"));
      toPrint.printVerbose();
    } else
    { //this variable did not exist
      System.out.println("Variable: "+command.getAttributeValue("variable")+" is undefined.");
    }
  }
  
  /**
   * Displays a graphical plotting of this variable. Each point is color coded
   * based on it's value. Points outside the variable are displayed as white.
   * @param command XML node defining the operation.
   */
  private void plotCommand(Element command)
  {
    Variable toPrint;
    
    if(variableList.containsKey(command.getAttributeValue("variable")))
    {
      toPrint = (Variable)variableList.get(command.getAttributeValue("variable"));
      if(toPrint.isReference())
      {
        if(toPrint.isGroup())
        {
          double min, max;
          double[][] matrix;
          MatrixGrapher graph;
          GroupVariable grp = (GroupVariable)toPrint;
          ReferenceVariable currPrint;
          Map.Entry me;
          
          Iterator it = grp.data.entrySet().iterator();
          while(it.hasNext())
          {
            me = (Map.Entry)it.next();
            currPrint = (ReferenceVariable)me.getValue();
            graph = new MatrixGrapher();
            
            min = (smallestValue(currPrint.getData()))[0].data[0][0];
            max = (largestValue(currPrint.getData()))[0].data[0][0];
            graph.drawMatrix(currPrint.buildMatrix(), min, max, currPrint.x, currPrint.y, currPrint.res);
          }
        } else
        {
          ReferenceVariable ref = (ReferenceVariable)toPrint;
          double min, max;
          double[][] matrix;
          MatrixGrapher graph = new MatrixGrapher();
          
          min = (smallestValue(ref.getData()))[0].data[0][0];
          max = (largestValue(ref.getData()))[0].data[0][0];
          graph.drawMatrix(ref.buildMatrix(), min, max, ref.x, ref.y, ref.res);
        }
      } else
      { //variable is just data
        System.out.println("Variable: "+command.getAttributeValue("variable")+" does not contain reference information.");
      }
    } else
    { //this variable did not exist
      System.out.println("Variable: "+command.getAttributeValue("variable")+" is undefined.");
    }
  }
  
  /**
   * Creates a new 'txt' file containing this variable's data. This will allow
   * a user to perform manipulations on data, then save that data and reread
   * it back into the preprocessor later so they can start working at the point
   * they left off. The resulting data set can be quickly added to old working
   * data by using the seed command in the preprocessor.
   * @param command XML node defining the operation.
   */
  private void createDataSetCommand(Element command)
  {
    Variable var;
    ReferenceVariable varR;
    String varName;
    String hold;
    Element currInfo, currTag;
    
    currInfo = command.getChild("source");
    if(variableList.containsKey(currInfo.getAttributeValue("name")))
    {
      varName = currInfo.getAttributeValue("name");
      var = (Variable)variableList.get(varName);
      if((var.isReference())&&(!var.isGroup()))
      {//this exists and is a reference variable, now make me a file!
//getting information and printin to file
        varR = (ReferenceVariable)var;
        try
        {
          currInfo = command.getChild("file");
          String outFile = currInfo.getAttributeValue("name");
          BufferedWriter out = new BufferedWriter( new java.io.FileWriter(outFile));
          currInfo = command.getChild("tag");
          if((Boolean.valueOf(currInfo.getAttributeValue("value"))).booleanValue())
          { //write tags to the beginning of this file
            //writing the name of this data
            currTag = currInfo.getChild("fieldName");
            if(currTag == null)
            {
              out.write(varName+""); //using variable name as the data name
              out.newLine();
            } else
            { //using the passed value as the field name
              out.write(currTag.getAttributeValue("value")+"");
              out.newLine();
            }
            
            //writing the time these values exist at
            currTag = currInfo.getChild("time"); //they have to give a time, no if statement
            out.write(currTag.getAttributeValue("value")+"");
            out.newLine();	
            
            //writing the resolution of this data
            out.write(varR.res+"");
            out.newLine();
            
            //writing whether or not this data is averaged on addition
            out.write(varR.avg+"");
            out.newLine();
            
            //writing that values are output in decimal form
            out.write("decimal");
            out.newLine();
            
            //writing this data's units if they exist or noUnit if they dont
            if(dataUnits.containsKey(varName))
            {
              hold = (String)dataUnits.get(varName);
              
            } else
            {
              hold = "noUnit"; //have to print somethinb because of static reading in preprocess
            }
            out.write(hold+"");
            out.newLine();
            
            //writing this data's refrence if it exists or no reference if it does not
            if(dataRef.containsKey(varName))
            {
              hold = (String)dataRef.get(varName);
              
            } else
            {
              hold = "no reference"; //always reads the same fields, as with units
            }
            out.write(hold+"");
            out.newLine();
            
          } //done writing tags
          //writing the matrix of values
          double[][] toPrint = varR.buildMatrix();
          for(double iY = 90; iY > (-90); iY-=varR.res)
          {
            if(((iY >= varR.y+varR.res))&&(iY <= (varR.y+varR.h)))
            { //this whole line is inside the y bounds
              for(double iX = -180; iX < (180); iX+=varR.res)
              {
                if(((iX >= varR.x))&&(iX <= (varR.x+varR.w-varR.res)))
                { //this point is inside x bounds of var, print this location
                  if(Double.isNaN(toPrint[(int)(((varR.y+varR.h)-iY)/varR.res)][(int)((iX-varR.x)/varR.res)]))
                  { //we dont have a value for this point, just print 0
                    out.write("0.0 ");
                  } else
                  { //do have a value, print it!
                    out.write(toPrint[(int)(((varR.y+varR.h)-iY)/varR.res)][(int)((iX-varR.x)/varR.res)]+" ");
                  }
                } else
                { //out of x bounds, print 0
                  out.write("0.0 ");
                }
              }
            } else
            { //this whole line is out of the y bounds, just print 0's
              for(double iX = -180; iX < (180); iX+=varR.res)
              {
                out.write("0.0 ");
              }
            }
            out.newLine();
          }//done writing matrix
          
          
          out.flush();
          out.close();
        } catch(IOException e)
        {
          System.out.println("IOException in -> createDataSetCommand");
        }
//done making the file
      } else
      {
        System.out.println("Variable: "+currInfo.getAttributeValue("name")+" is not a reference variable.");
      }
    } else
    {
      System.out.println("Variable: "+currInfo.getAttributeValue("name")+" is undefined.");
    }
  }
  
  /**
   * Adds a descriptive comment to a variable which will be printed with
   * that variable if the verbose option is selected.
   * @param command XML node defining the operation.
   */
  private void commentCommand(Element command)
  {
    Variable var;
    Element currInfo;
    
    currInfo = command.getChild("variable");
    if(variableList.containsKey(currInfo.getAttributeValue("value")))
    {
      var = (Variable)variableList.get(currInfo.getAttributeValue("value"));
      currInfo = command.getChild("text");
      var.comment = currInfo.getAttributeValue("value");
    } else
    {
      System.out.println("Variable: "+currInfo.getAttributeValue("value")+" is undefined.");
    }

  }
  
  /**
   * Adds a reference descriptor to the variable which will be printed with the
   * printVerbose command.
   * @param command XML node defining the operation.
   */
  private void setReferenceCommand(Element command)
  {
    Variable var;
    Element currInfo;
    
    currInfo = command.getChild("variable");
    if(variableList.containsKey(currInfo.getAttributeValue("value")))
    {
      var = (Variable)variableList.get(currInfo.getAttributeValue("value"));
      currInfo = command.getChild("text");
      if(var.isReference())
      {
        ((ReferenceVariable)var).reference = currInfo.getAttributeValue("value");
      } else
      {
        System.out.println("Variable: "+currInfo.getAttributeValue("value")+" is not a reference variable.");
      }
    } else
    {
      System.out.println("Variable: "+currInfo.getAttributeValue("value")+" is undefined.");
    }

  }
  
  /**
   * Adds a units descriptor to the variable which will be printed with the
   * printVerbose command.
   * @param command XML node defining the operation.
   */
  private void setUnitsCommand(Element command)
  {
    Variable var;
    Element currInfo;
    
    currInfo = command.getChild("variable");
    if(variableList.containsKey(currInfo.getAttributeValue("value")))
    {
      var = (Variable)variableList.get(currInfo.getAttributeValue("value"));
      currInfo = command.getChild("text");
      if(var.isReference())
      {
        ((ReferenceVariable)var).units = currInfo.getAttributeValue("value");
      } else
      {
        System.out.println("Variable: "+currInfo.getAttributeValue("value")+" is not a reference variable.");
      }
    } else
    {
      System.out.println("Variable: "+currInfo.getAttributeValue("value")+" is undefined.");
    }

  }
  
//*****************************************************************************
//*****************Component Manipulators**************************************
//*****************************************************************************
  
  private Wrapper[] addVar(Wrapper[] R1, Wrapper[] R2)
  {
    double[][] holdM1, holdM2, holdMR;
    Wrapper[] toReturn = new Wrapper[R1.length];
    
    for(int i = 0; i < R1.length; i++)
    {
      holdM1 = R1[i].data;
      holdM2 = R2[i].data;
      holdMR = new double[holdM1.length][holdM1[0].length];
      for(int iY = 0; iY < holdM1.length; iY++)
      {
        for(int iX = 0; iX < holdM1[0].length; iX++)
        {
          if((Double.isNaN(holdM1[iY][iX]))||(Double.isNaN(holdM2[iY][iX])))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            holdMR[iY][iX] = holdM1[iY][iX] + holdM2[iY][iX];
          }
        }
      }
      
      toReturn[i] = R1[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] addVar(Wrapper[] R, double change)
  {
    double[][] holdMR, holdMS;
    Matrix m1, m2, mR;
    Wrapper[] toReturn = new Wrapper[R.length];
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMR.length; iY++)
      {
        for(int iX = 0; iX < holdMR[0].length; iX++)
        {
          if(Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            holdMR[iY][iX] = holdMS[iY][iX] + change;
          }
        }
      }
      
      toReturn[i] = R[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] subtractVar(Wrapper[] R1, Wrapper[] R2)
  {
    double[][] holdM1, holdM2, holdMR;
    Matrix m1, m2, mR;
    Wrapper[] toReturn = new Wrapper[R1.length];
    
    for(int i = 0; i < R1.length; i++)
    {
      holdM1 = R1[i].data;
      holdM2 = R2[i].data;
      holdMR = new double[holdM1.length][holdM1[0].length];
      for(int iY = 0; iY < holdM1.length; iY++)
      {
        for(int iX = 0; iX < holdM1[0].length; iX++)
        {
          if((Double.isNaN(holdM1[iY][iX]))||(Double.isNaN(holdM2[iY][iX])))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            holdMR[iY][iX] = holdM1[iY][iX] - holdM2[iY][iX];
          }
        }
      }
      
      toReturn[i] = R1[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] multiplyVar(Wrapper[] R1, Wrapper[] R2)
  {
    double[][] holdM1, holdM2, holdMR;
    Wrapper[] toReturn = new Wrapper[R1.length];
    
    for(int i = 0; i < R1.length; i++)
    {
      holdM1 = R1[i].data;
      holdM2 = R2[i].data;
      holdMR = new double[holdM1.length][holdM1[0].length];
      for(int iY = 0; iY < holdM1.length; iY++)
      {
        for(int iX = 0; iX < holdM1[0].length; iX++)
        {
          if((Double.isNaN(holdM1[iY][iX]))||(Double.isNaN(holdM2[iY][iX])))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            holdMR[iY][iX] = (holdM1[iY][iX] * holdM2[iY][iX]);
          }
        }
      }
      
      toReturn[i] = R1[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] multiplyVar(Wrapper[] R, double factor)
  {
    double[][] holdMR, holdMS;
    Matrix m1, m2, mR;
    Wrapper[] toReturn = new Wrapper[R.length];
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMR.length; iY++)
      {
        for(int iX = 0; iX < holdMR[0].length; iX++)
        {
          if(Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            holdMR[iY][iX] = (holdMS[iY][iX] * factor);
          }
        }
      }
      
      toReturn[i] = R[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] divideVar(Wrapper[] R1, Wrapper[] R2)
  {
    double[][] holdM1, holdM2, holdMR;
    Wrapper[] toReturn = new Wrapper[R1.length];
    
    for(int i = 0; i < R1.length; i++)
    {
      holdM1 = R1[i].data;
      holdM2 = R2[i].data;
      holdMR = new double[holdM1.length][holdM1[0].length];
      for(int iY = 0; iY < holdM1.length; iY++)
      {
        for(int iX = 0; iX < holdM1[0].length; iX++)
        {
          if((Double.isNaN(holdM1[iY][iX]))||(Double.isNaN(holdM2[iY][iX])))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            holdMR[iY][iX] = (holdM1[iY][iX] / holdM2[iY][iX]);
          }
          
        }
      }
      
      toReturn[i] = R1[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] divideVar(Wrapper[] R, double factor)
  {
    double[][] holdMR, holdMS;
    Matrix m1, m2, mR;
    Wrapper[] toReturn = new Wrapper[R.length];
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMR.length; iY++)
      {
        for(int iX = 0; iX < holdMR[0].length; iX++)
        {
          if(Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            holdMR[iY][iX] = (holdMS[iY][iX] / factor);
          }
        }
      }
      
      toReturn[i] = R[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] greaterThan(Wrapper[] R, double limit)
  {
    double[][] holdMR;
    double[][] holdMS;
    Wrapper[] toReturn = new Wrapper[R.length];
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMR.length; iY++)
      {
        for(int iX = 0; iX < holdMR[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            if(holdMS[iY][iX] > limit)
          	{
            	holdMR[iY][iX] = holdMS[iY][iX];
          	} else
          	{
            	holdMR[iY][iX] = 0;
          	}
          } else
          {
            holdMR[iY][iX] = Double.NaN;
          }
        }
      }

      toReturn[i] = R[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] greaterThanRegion(Wrapper[] R, Wrapper[] M)
  { 
    double[][] holdMR;
    double[][] holdMS;
    double[][] holdMM; //matrix mask
    Wrapper[] toReturn = new Wrapper[R.length];
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMM = M[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMR.length; iY++)
      {
        for(int iX = 0; iX < holdMR[0].length; iX++)
        {
          if((!Double.isNaN(holdMS[iY][iX]))&&(!Double.isNaN(holdMM[iY][iX])))
          {
            if(holdMS[iY][iX] > holdMM[iY][iX])
            {
              holdMR[iY][iX] = holdMS[iY][iX];
            } else
            {
              holdMR[iY][iX] = 0;
            }
          } else
          {
            holdMR[iY][iX] = Double.NaN;
          }
        }
      }

      toReturn[i] = R[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] lessThan(Wrapper[] R, double limit)
  {
    double[][] holdMR;
    double[][] holdMS;
    Wrapper[] toReturn = new Wrapper[R.length];
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMR.length; iY++)
      {
        for(int iX = 0; iX < holdMR[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            if(holdMS[iY][iX] < limit)
          	{
            	holdMR[iY][iX] = holdMS[iY][iX];
          	} else
          	{
            	holdMR[iY][iX] = 0;
          	}
          } else
          {
            holdMR[iY][iX] = Double.NaN;
          }
        }
      }

      toReturn[i] = R[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] lessThanRegion(Wrapper[] R, Wrapper[] M)
  {
    double[][] holdMR;
    double[][] holdMS;
    double[][] holdMM; //matrix mask
    Wrapper[] toReturn = new Wrapper[R.length];
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMM = M[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMR.length; iY++)
      {
        for(int iX = 0; iX < holdMR[0].length; iX++)
        {
          if((!Double.isNaN(holdMS[iY][iX]))&&(!Double.isNaN(holdMM[iY][iX])))
          {
            if(holdMS[iY][iX] < holdMM[iY][iX])
            {
              holdMR[iY][iX] = holdMS[iY][iX];
            } else
            {
              holdMR[iY][iX] = 0;
            }
          } else
          {
            holdMR[iY][iX] = Double.NaN;
          }
        }
      }

      toReturn[i] = R[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }
  private Wrapper[] countGreaterThan(Wrapper[] R, double limit)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if((holdMS[iY][iX] > limit)&&(!Double.isNaN(holdMS[iY][iX])))
          {
            holdMR[0][0]++;
          }
        }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] countGreaterThanRegion(Wrapper[] R, Wrapper[] M)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    double[][] holdMM; //matrix mask
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMM = M[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if((holdMS[iY][iX] > holdMM[iY][iX])&&(!Double.isNaN(holdMS[iY][iX])))
          {
            holdMR[0][0]++;
          }
        }
    }
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] countLessThan(Wrapper[] R, double limit)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if((holdMS[iY][iX] < limit)&&(!Double.isNaN(holdMS[iY][iX])))
          {
            holdMR[0][0]++;
          }
        }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] countLessThanRegion(Wrapper[] R, Wrapper[] M)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    double[][] holdMM; //matrix mask
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      holdMM = M[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if((holdMS[iY][iX] < holdMM[iY][iX])&&(!Double.isNaN(holdMS[iY][iX])))
          {
            holdMR[0][0]++;
          }
        }
    }
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] countElements(Wrapper[] R)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[0][0]++;
          }
        }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] sumValues(Wrapper[] R)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[0][0] += holdMS[iY][iX];
          }
        }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] sumValues(Wrapper[] R, double[][] weight, double x, double y, double h)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    int wX, wY; //the double indexs for weight
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
      {
        wY = (int)(((iY*R[i].getRes())+((y+h)-(R[i].getY()+R[i].getH())))/R[i].getRes());
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            wX = (int)((((iX*R[i].getRes())+R[i].getX())-x)/R[i].getRes());
            holdMR[0][0] += (holdMS[iY][iX]*weight[wY][wX]);
          }
        }
      }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] largestValue(Wrapper[] R)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = Double.NaN;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            if(Double.isNaN(holdMR[0][0]))
            {
              holdMR[0][0] = holdMS[iY][iX];
            } else if((holdMS[iY][iX] > holdMR[0][0]))
            {
              holdMR[0][0] = holdMS[iY][iX];
            }
          }
        }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] smallestValue(Wrapper[] R)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    
    holdMR[0][0] = Double.NaN;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            if(Double.isNaN(holdMR[0][0]))
            {
              holdMR[0][0] = holdMS[iY][iX];
            } else
            {
              if(holdMS[iY][iX] < holdMR[0][0])
              {
                holdMR[0][0] = holdMS[iY][iX];
              }
            }
          }
        }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] avgOverRegion(Wrapper[] R)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    int count = 0;
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[0][0] += holdMS[iY][iX];
            count++;
          }
        }
    }
    holdMR[0][0] = (holdMR[0][0]/count);
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] avgOverRegion(Wrapper[] R, double[][] weight, double x, double y, double h)
  {
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    int count = 0;
    int wY, wX;
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
      {
        wY = (int)(((iY*R[i].getRes())+((y+h)-(R[i].getY()+R[i].getH())))/R[i].getRes());
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            wX = (int)((((iX*R[i].getRes())+R[i].getRes())-x)/R[i].getRes());
            holdMR[0][0] += (holdMS[iY][iX]*weight[wY][wX]);
            count++;
          }
        }
      }
    }
    holdMR[0][0] = (holdMR[0][0]/count);
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] avgVariables(Wrapper[][] data)
  {
    double[][] holdMS;
    Wrapper[] toReturn = new Wrapper[data[0].length];
    
    for(int i = 0; i < data.length; i++)
    {
      toReturn[i] = data[0][i].makeCopy();
      toReturn[i].data = new double[data[0][i].data.length][data[0][i].data[0].length];
    }
    
    for(int i = 0; i < data.length; i++)
    { //for each variable
      for(int k = 0; k < data[i].length; k++)
      { //for each wrapper
        holdMS = data[i][k].data;
        for(int iY = 0; iY<holdMS.length; iY++)
        {
          for(int iX = 0; iX<holdMS[0].length; iX++)
          {
            if(!Double.isNaN(holdMS[iY][iX]))
            {
              toReturn[k].data[iY][iX] += holdMS[iY][iX];
            }
          }
        }
      }
    }
    
    for(int i = 0; i < data.length; i++)
    { //for each wrapper
        for(int iY = 0; iY<toReturn[0].data.length; iY++)
        {
          for(int iX = 0; iX<toReturn[0].data[0].length; iX++)
          {
            toReturn[i].data[iY][iX] = (toReturn[i].data[iY][iX]/data.length);
          }
        }
    }
    
    return toReturn;
  }
  private Wrapper[] avgOverRegionByArea(Wrapper[] R, double Rx, double Ry, double Rw, double Rh)
  { //for use by avg variables because weight has already been factored in
    double POLAR_CIRCUM = 40008.00; //these are constand but i dont know how to make constants in java...
    double EQUAT_CIRCUM = 40076.5;
    double PI = 3.1415926535;
    
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    DataWrapper[] toReturn = new DataWrapper[1];
    int count = 0;
    
    double circumAtLat; //the circumference of the earth at a specific latitude
    double totalWidth; //width in km of the region
    double totalHeight; //height in km of the region
    double blockWidth; //eidth in km of a block of data
    double blockHeight; //height in km of a block of data
    double totalArea; //the area of the ENTIRE region
    double proportion; //the proportion of the whole region a block of data is
    
    //finding the area of the master region, for getting proportions
    circumAtLat = Math.abs(EQUAT_CIRCUM*Math.cos((Ry)*(PI/180)));
    double lowWidth = (circumAtLat/(360/(Rw)));
    circumAtLat = Math.abs(EQUAT_CIRCUM*Math.cos((Ry+Rh)*(PI/180)));
    double highWidth = (circumAtLat/(360/(Rw)));
    totalHeight = (POLAR_CIRCUM/(360/(Rh)));
    totalArea = ((highWidth+lowWidth)/2)*totalHeight;
    //done getting that!
    
    holdMR[0][0] = 0;
    
    for(int i = 0; i < R.length; i++)
    {
      totalHeight = (POLAR_CIRCUM/(360/R[i].getH()));
      blockHeight = (totalHeight/R[i].data.length);
      
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
      {
        circumAtLat = Math.abs(EQUAT_CIRCUM*Math.cos((R[i].getY()+(iY*R[i].getRes()))*(PI/180)));
        totalWidth = (circumAtLat/(360/R[i].getW()));
        blockWidth = (totalWidth/R[i].data[iY].length);
        proportion = ((blockWidth*blockHeight)/(totalArea));
        
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[0][0] += (holdMS[iY][iX]*proportion);
          }
        }
      }
    }
    
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] avgOverRegionByArea(Wrapper[] R, double[][] weight, double Rx, double Ry, double Rw, double Rh)
  {
    double POLAR_CIRCUM = 40008.00; //these are constand but i dont know how to make constants in java...
    double EQUAT_CIRCUM = 40076.5;
    double PI = 3.1415926535;
    
    double[][] holdMR = new double[1][1];
    double[][] holdMS;
    double total = 0;
    DataWrapper[] toReturn = new DataWrapper[1];
    int count = 0;
    int wY, wX;
    
    double circumAtLat; //the circumference of the earth at a specific latitude
    double totalWidth; //width in km of the region
    double totalHeight; //height in km of the region
    double blockWidth; //eidth in km of a block of data
    double blockHeight; //height in km of a block of data
    double totalArea; //the area of the ENTIRE region (done as a trapazoid to acount for diff widths)
    double proportion; //the proportion of the whole region a block of data is
    
    //finding the area of the master region, for getting proportions
    circumAtLat = Math.abs(EQUAT_CIRCUM*Math.cos((Ry)*(PI/180)));
    double lowWidth = (circumAtLat/(360/(Rw)));
    circumAtLat = Math.abs(EQUAT_CIRCUM*Math.cos((Ry+Rh)*(PI/180)));
    double highWidth = (circumAtLat/(360/(Rw)));
    totalHeight = (POLAR_CIRCUM/(360/(Rh)));
    totalArea = ((highWidth+lowWidth)/2)*totalHeight;
    //done getting that!
    
    holdMR[0][0] = 0;
    for(int i = 0; i < R.length; i++)
    {
      totalHeight = (POLAR_CIRCUM/(360/(R[i].getH())));
      blockHeight = (totalHeight/R[i].data.length);
      
      holdMS = R[i].data;
      for(int iY = 0; iY < holdMS.length; iY++)
      {
        wY = (int)(((iY*R[i].getRes())+((Ry+Rh)-(R[i].getY()+R[i].getH())))/R[i].getRes());
        circumAtLat = Math.abs(EQUAT_CIRCUM*Math.cos((R[i].getY()+((holdMS.length-iY)*R[i].getRes()))*(PI/180)));
        totalWidth = (circumAtLat/(360/(R[i].getW())));
        blockWidth = (totalWidth/R[i].data[iY].length);
        proportion = ((blockWidth*blockHeight)/(totalArea));
        
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(!Double.isNaN(holdMS[iY][iX]))
          {
            wX = (int)((((iX*R[i].getRes())+R[i].getX())-Rx)/R[i].getRes());
            holdMR[0][0] += (holdMS[iY][iX]*proportion*weight[wY][wX]);
          }
        }
      }
    }
    
    total /= R.length;
    toReturn[0] = new DataWrapper();
    toReturn[0].data = holdMR;
    return toReturn;
  }
  private Wrapper[] weightValues(Wrapper[] R1, Wrapper[] R2, double minVal, double maxVal, double minWeight, double maxWeight)
  {
    double[][] holdMS, holdMW, holdMR;
    double toTest, p1, p2;
    double thisWeight;
    double weightDiff = (maxWeight-minWeight);
    double valDiff = (maxVal-minVal);
    Wrapper[] toReturn = new Wrapper[R1.length];
    
    for(int i = 0; i < R1.length; i++)
    {
      holdMS = R1[i].data;
      holdMW = R2[i].data;
      holdMR = new double[holdMS.length][holdMS[0].length];
      for(int iY = 0; iY < holdMS.length; iY++)
      {
        for(int iX = 0; iX < holdMS[0].length; iX++)
        {
          if(Double.isNaN(holdMS[iY][iX]))
          {
            holdMR[iY][iX] = Double.NaN;
          } else
          {
            toTest = holdMW[iY][iX];
            if(toTest < minVal)
            {
              toTest = minVal;
            }
            if(toTest > maxVal)
            {
              toTest = maxVal;
            }
            toTest -= minVal;
            p1 = (toTest/valDiff);
            p2 = (p1*weightDiff);
            thisWeight = (p2+minWeight);
            
            holdMR[iY][iX] = (holdMS[iY][iX] * thisWeight);
          }
        }
      }
      
      toReturn[i] = R1[i].makeCopy();
      toReturn[i].data = holdMR;
    }
    return toReturn;
  }

//*****************************************************************************
//*******************Component Functions***************************************
//*****************************************************************************
  /**
   * Special reading function which gets all supplementary field information.
   * This includes whether a field is averaged or added on aggregation, the
   * reference for the field, and the units of the field.
   * @param elem XML element which contains additional data for all fields.
   */
  private void setFieldInfo(Element elem)
  {
    String dataName;
    boolean avg;
    String ref;
    String units;
    Element currElem;
    Element currInfo;
    List fields = elem.getChildren("variable");
    
    for(int i = 0; i < fields.size(); i++)
    {
      currElem = (Element)fields.get(i);
      dataName = currElem.getAttributeValue("name");
      
      currInfo = currElem.getChild("average");
      avg = (Boolean.valueOf(currInfo.getAttributeValue("value"))).booleanValue();
      dataAvgAdd.put(dataName, new Boolean(avg));
      
      currInfo = currElem.getChild("reference");
      if(currInfo != null)
      {
        ref = currInfo.getAttributeValue("value");
        dataRef.put(dataName, ref);
      }
      
      currInfo = currElem.getChild("units");
      if(currInfo != null)
      {
        units = currInfo.getAttributeValue("value");
        dataUnits.put(dataName, units);
      }
    }
  }
  
  /**
   * Creates a new region and fills it with data based on the passed XML element.
   * The created region is defined as a sub-region, a region on the lowest lvl.
   * This means that it contains only data and no other regions. Data in
   * the xml element is defined in a tree structure of variables->times->data.
   * @param currRegion XML element which contains a region worth of data.
   */
  private void addRegion(Element currRegion)
  {
    //creating a new region from the passed element and adding it to the master list
    
    int sizeX, sizeY, currX, currY;
    boolean avg;
    TreeMap toAddVar;
    double[][] toAddTime;
    String varName, timeName;
    List varList, timeList, dataList;
    Element currVar, currTime, currData;
    
    subRegion toAdd = new subRegion();
    toAdd.name = currRegion.getAttributeValue("name");
    toAdd.resolution = resolution;
    toAdd.x = Double.parseDouble(currRegion.getAttributeValue("x"));
    toAdd.y = Double.parseDouble(currRegion.getAttributeValue("y"));
    sizeX = Integer.parseInt(currRegion.getAttributeValue("sizeX"));
    sizeY = Integer.parseInt(currRegion.getAttributeValue("sizeY"));
    toAdd.width = (sizeX*resolution);
    toAdd.height = (sizeY*resolution);
    
  //special case to get the weight variable
    currVar = currRegion.getChild("weight");
    varName = "weight";
    toAddVar = new TreeMap();
    //begin getting times loop
    timeList = currVar.getChildren("time");
    for(int j = 0; j<timeList.size(); j++)
    {
      currTime = (Element)timeList.get(j);
      timeName = currTime.getAttributeValue("value");
      toAddTime = new double[sizeY][sizeX];
      for(int hy = 0; hy<sizeY; hy++)
      {
        for(int hx = 0; hx<sizeX; hx++)
        {
          toAddTime[hy][hx] = 0;
        }
      }
      //begin getting data loop
      dataList = currTime.getChildren("data");
      for(int k = 0; k<dataList.size(); k++)
      {
        currData = (Element)dataList.get(k);
        currX = Integer.parseInt(currData.getAttributeValue("x"));
        currY = Integer.parseInt(currData.getAttributeValue("y"));
        toAddTime[currY][currX] = stringToDouble(currData.getAttributeValue("value"));
      }
      //end getting data
      toAddVar.put(timeName, toAddTime);
    }
    //end getting time
    toAdd.data.put(varName, toAddVar);
  //end getting weight
    
    //begin getting variables loop
    varList = currRegion.getChildren("variable");
    for(int i = 0; i < varList.size(); i++)
    {
      currVar = (Element)varList.get(i);
      varName = currVar.getAttributeValue("value");
      avg = ((Boolean)dataAvgAdd.get(varName)).booleanValue();
      toAddVar = new TreeMap();
      //begin getting times loop
      timeList = currVar.getChildren("time");
      for(int j = 0; j<timeList.size(); j++)
      {
        currTime = (Element)timeList.get(j);
        timeName = currTime.getAttributeValue("value");
        toAddTime = new double[sizeY][sizeX];
        for(int hy = 0; hy<sizeY; hy++)
        {
          for(int hx = 0; hx<sizeX; hx++)
          {
            toAddTime[hy][hx] = Double.NaN;
          }
        }
        //begin getting data loop
        dataList = currTime.getChildren("data");
        for(int k = 0; k<dataList.size(); k++)
        {
          currData = (Element)dataList.get(k);
          currX = Integer.parseInt(currData.getAttributeValue("x"));
          currY = Integer.parseInt(currData.getAttributeValue("y"));
          toAddTime[currY][currX] = stringToDouble(currData.getAttributeValue("value"));
          if(!avg)
          { //this is an additive value and should be initially weighted (now)
            toAddTime[currY][currX] *= ((double[][])((TreeMap)toAdd.data.get("weight")).get("0"))[currY][currX];
          }
        }
        //end getting data
        toAddVar.put(timeName, toAddTime);
      }
      //end getting time
      toAdd.data.put(varName, toAddVar);
    }
    //end getting varialbes
    
    //adding region to master list
    regionList.put(toAdd.name, toAdd);
  }

  /**
   * Creates a new region based on a list of contained regions which have been
   * previously defined. The contained regions must all be of lower level,
   * where level is defined as one more than the greatest level of a contained
   * region, or as 0 for subregions. The created region will store data in the
   * form it gets it in, as a collection of matrices one for each contained 
   * sub-region.
   * @param currRegion XML element which contains a list of contained regions.
   */
  private void addSuperRegion(Element currRegion)
  {
    List subRegions;
    Region sub;
    double mX, mY;
    
    superRegion toAdd = new superRegion();
    
    toAdd.name = currRegion.getAttributeValue("name");
    toAdd.resolution = resolution;
    
    //begin loop to add subregions to this superregion
    subRegions = currRegion.getChildren("region");
    for(int i = 0; i < subRegions.size(); i++)
    {
      if(regionList.containsKey(((Element)subRegions.get(i)).getAttributeValue("name")))
      {
        sub = (Region)regionList.get(((Element)subRegions.get(i)).getAttributeValue("name"));
        toAdd.data.add(sub);
        toAdd.numSub += sub.numSub;
        //updating superregions bounds if necessary
        if(toAdd.height==-1)
        { //this is the first block being added to this region nothing to
          // test against yet
          toAdd.y = sub.y;
          toAdd.x = sub.x;
          toAdd.height = sub.height;
          toAdd.width = sub.width;
        } else
        { //test against old bounds, if outside them, change them
          if(sub.y < toAdd.y)
          {
            toAdd.height += (toAdd.y-sub.y);
            toAdd.y = sub.y;
          }
          if((sub.y+sub.height)>(toAdd.y+toAdd.height))
          {
            toAdd.height = ((sub.y+sub.height)-toAdd.y);
          }
          if(sub.x<toAdd.x)
          {
            toAdd.width += (toAdd.x-sub.x);
            toAdd.x = sub.x;
          }
          if((sub.x+sub.width)>(toAdd.x+toAdd.width))
          {
            toAdd.width = ((sub.x+sub.width)-toAdd.x);
          }
        }
      } else
      {
        System.out.println(toAdd.name+" -> "+((Element)subRegions.get(i)).getAttributeValue("name")+" : tried to add but region WAS NOT FOUND");
      }
    }
    //end adding regions
    
    regionList.put(toAdd.name, toAdd);
  }

//*****************************************************************************
//*********************Helper Functions****************************************
//*****************************************************************************
  /**
   * Creates all of the documents and read and write streams which will
   * be used by this class. Is automatically run by {@link runAll()}.
   */
  private void makeStreams()
  {
    //this function initializes all of the XML documents
    //i will add the code for additional readers as i need them
    try
    {
      SAXBuilder builder = new SAXBuilder();
      dDocument = builder.build(dSource);
      rDocument = builder.build(rSource);
      cDocument = builder.build(cSource);
    } catch(FileNotFoundException e)
    {
      System.out.println("FileNotFound! oh noes! in -> makeStreams");
    } catch(JDOMException e)
    {
      System.out.println("JDOM Exception! grarrrr! in -> makeStreams");
    }
    catch(IOException e)
    {
      System.out.println("IOException! for shame! in -> makeStreams");
    }
  }
  /**
   * Reads a whitespace delimited word from the provided {@link BufferedReader}.
   * This is a convenience function so that input does not have to be gathered
   * character by characer.
   * @param input BufferedReader from which to gather a 'word' of input.
   * @return First word residing in the passed reader.
   */
  private String readWord(BufferedReader input)
  {
    //reads an entire word from an input stream rather than just a character
    //words delimited by any whitespace 'space, new line, tab'
    String build = new String();
    int read;
    char hold;
    try {
      while(((read = input.read()) != -1)&&((hold = (char)read) != ' ')&&(hold != '\n')&&(hold != '\t'))
      {
        build = build.concat(String.valueOf(hold));
      }
    } catch (IOException ex)
    {
      System.out.println("IOException!!!");
    }

    if(build.length() > 0)
      return build.trim();
    else
      return null;
  }
  /**
   * Takes a string representation of a number in scientific form and returns
   * a double containing that value. Assumes 'E' or 'e' will be used as the
   * demarkation between mantissa and exponent. Following 'e' seperator will be
   * a + or - sign denoting whether the exponent should be positive or
   * negative.
   * @param sc String to be parsed into a double.
   * @return Double representation of supplied string.
   */
  private double stringToDouble(String sc)
  {
    //takes a string of the form #.###E+### and converts it to a double
    double mantissa, exponent, expValue;
    boolean expSignPos = false;
    int E = sc.indexOf('E');
    if(E == -1)
    {
      E = sc.indexOf('e');
    }
    if(E == -1)
    { //this is a normal double value, use Double.parseString
      return Double.parseDouble(sc);
    } else
    {
      mantissa = Double.parseDouble(sc.substring(0, E));
      if(sc.substring(E+1, E+2).equals("+"))
        expSignPos = true;
      exponent = Double.parseDouble(sc.substring(E+2, sc.length()));
      if(expSignPos)
        expValue = Math.pow(10, exponent);
      else
        expValue = Math.pow(10, (-1*exponent));

      if(mantissa!=0)
      {
        return mantissa*expValue;
      } else
      {
        return 0;
      }
    }
  }

  private void fillGroupByExplicit(GroupVariable var, Element members)
  {
    Element currMem;
    String currName;
    Variable currVar;
    List mems = members.getChildren("variable");
    
    for(int i = 0; i < mems.size(); i++)
    {
      currMem = (Element)mems.get(i);
      currName = currMem.getAttributeValue("value");
      if(variableList.containsKey(currName))
      {
        currVar = (Variable)variableList.get(currName);
        var.addData(currVar.getCopy());
      } else
      { //this variable doesnt actualyl exist, kick to null
        var = null;
        return;
      }
    }
  }
  private void fillGroupByTime(GroupVariable var, Element members)
  {
    Element currInfo;
    String reg, field;
    Region R;
    String[] timeList;
    Variable currVar;
    
    currInfo = members.getChild("region");
    reg = currInfo.getAttributeValue("value");
    currInfo = members.getChild("field");
    field = currInfo.getAttributeValue("value");
   
    boolean avg = ((Boolean)dataAvgAdd.get(field)).booleanValue();
    
    //get a list of times
    if(regionList.containsKey(reg))
    {
      R = (Region)regionList.get(reg);
      timeList = R.getTimeList(field);
      
      for(int i = 0; i < timeList.length; i ++)
      { //add each time entry as a seperate variable
        currVar = new ReferenceVariable(timeList[i], R, field, timeList[i], avg);
        var.addData(currVar);
      }
    } else
    { //cant very well add a region if it doesnt exist now can we
      var = null;
    }
  }
  private void fillGroupByExtraction(GroupVariable var, Element members)
  {
    Element currInfo;
    Variable VSource;
    ReferenceVariable currVar;
    String Vname, Rname;
    Region RShape;
    superRegion SR;
    Region[] regList;
    
    Vname = members.getAttributeValue("variable");
   
    if(variableList.containsKey(Vname))
    {
      VSource = (Variable)variableList.get(Vname);
      if(VSource.isReference())
      {
        Rname = ((ReferenceVariable)VSource).region;
        RShape = (Region)regionList.get(Rname);
        if(RShape.isSuper())
        {
          SR = (superRegion)RShape;
          regList = (Region[])(SR).data.toArray(new Region[0]);
          
          for(int i = 0; i < regList.length; i ++)
          { //add each child region as a seperate variable
            currVar = new ReferenceVariable(regList[i].name, regList[i]);
            currVar.avg = ((ReferenceVariable)VSource).avg;
            currVar.setData(regList[i].extractRegion((ReferenceVariable)VSource));
            
            var.addData(currVar);
          }
        } else
        {
          var = null;
          System.out.println("Variable "+Vname+" is subRegion and has no child regions.");
          return;
        }
      } else
      {
        var = null;
        System.out.println("Variable "+Vname+" is not a reference variable.");
        return;
      }
    } else
    {
      var = null;
      System.out.println("Variable "+Vname+" does not exist!");
      return;
    }
  }
  private void fillGroupByChildren(GroupVariable var, Element members)
  {
    Element currInfo;
    String reg, field, time;
    Region R;
    Region[] regList;
    Variable currVar;
    
    currInfo = members.getChild("region");
    reg = currInfo.getAttributeValue("value");
    currInfo = members.getChild("field");
    field = currInfo.getAttributeValue("value");
    currInfo = members.getChild("time");
    time = currInfo.getAttributeValue("value");
   
    boolean avg = ((Boolean)dataAvgAdd.get(field)).booleanValue();
    
    //get a list of times
    if(regionList.containsKey(reg))
    {
      R = (Region)regionList.get(reg);
      if(R.isSuper())
      {
        regList = (Region[])((superRegion)R).data.toArray(new Region[0]);
        for(int i = 0; i < regList.length; i ++)
        { //add each child region as a seperate variable
          currVar = new ReferenceVariable(regList[i].name, regList[i], field, time, avg);
          var.addData(currVar);
        }
      } else
      { //this i sjust a sub region, no children to fill with, null and kick
        var = null;
        System.out.println("Group variable "+var.name+" was seeded with a subRegion");
        return;
      }
    } else
    { //cant very well add a region if it doesnt exist now can we
      var = null;
    }
  }
  
  
//*****************************************************************************
}