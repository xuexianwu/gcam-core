#ifndef _SECTOR_H_
#define _SECTOR_H_
#pragma once

/*! 
* \file Sector.h
* \ingroup CIAM
* \brief The sector class header file.
* \author Sonny Kim
* \date $Date$
* \version $Revision$
*/

#include <vector>
#include <xercesc/dom/DOM.hpp>

using namespace std;
using namespace xercesc;

// Forward declarations
class subsector;
class Summary;
class Emcoef_ind;

/*! 
* \ingroup CIAM
* \brief A class which defines a single supply sector.
* \author Sonny Kim
*/

class sector
{
protected:
    string name; //!< sector name
    string unit; //!< unit of final product from sector
    string market; //!< regional market
    int nosubsec; //!< number of subsectors in each sector
    double tax; //!< sector tax or subsidy
    bool debugChecking; //!< General toggle to turn on various checks
    vector<subsector*> subsec; //!< subsector objects
    vector<double> sectorprice; //!< sector price in $/service
    vector<double> price_norm; //!< sector price normalized to base year
    vector<double> pe_cons; //!< sectoral primary energy consumption
    vector<double> input; //!< sector total energy consumption
    vector<double> output; //!< total amount of final output from sector
    vector<double> fixedOutput; //!< total amount of fixed output from sector
    vector<double> carbontaxpaid; //!< total sector carbon taxes paid
    vector<Summary> summary; //!< summary for reporting
    map<string,int> subSectorNameMap; //!< Map of subSector name to integer position in vector.
    virtual void initElementalMembers();
    
public:
   
    sector();
    virtual ~sector();
    virtual void clear();
    string getName(); // return name of sector
    virtual void XMLParse( const DOMNode* node );
    void completeInit();
    virtual void XMLDerivedClassParse( const string nodeName, const DOMNode* curr ); // for derived classes
    virtual void XMLDerivedClassParseAttr( const DOMNode* node ); // for derived classes
    virtual void toXML( ostream& out ) const;
    virtual void toOutputXML( ostream& out ) const;
    virtual void toXMLDerivedClass( ostream& out ) const;
    virtual void toDebugXML( const int period, ostream& out ) const;
    virtual void setMarket( const string& regname ); //create markets
    void applycarbontax( const string& regionName, double tax,int per); // passes along regional carbon tax
    void addghgtax( const string ghgname, const string regionName, const int per); // sets ghg tax to technologies
    virtual void calc_share( const string regionName, const int per, const double gnp_cap = 1 ); // calculates and normalizes shares 
    virtual void price(int per); // calculates sector price
    void adjSharesCapLimit( const string regionName, const int per ); // adjust for capacity limit
    void checkShareSum( const string regionName, const int per ); // check that shares sum to unity
    void initCalc( const string& regionName, const int per ); // Perform initializations
    void production( const string& regionName,int per); // calculates production using mrk prices
    virtual void calibrateSector( const string regionName, const int per ); // sets demand to totoutput and output
    void setoutput(const string& regionName, double dmd, int per); // sets demand to totoutput and output
    void sumoutput(int per); // sum subsector outputs
    void set_ser_dmd(double dmd, int per); // sets end-use sector service demand
    void supply( const string regionName, const int per ); // calculates supply from each subsector
    void show();
    void showsubsec(int per, const char* ofile); // shows all subsectors in the sector
    void showlabel(const char* ofile); // show sector label
    int shownosubsec(void);
    double getoutput(int per); // returns sector output 
    double getFixedSupply(int per) const; // returns sector output 
    bool sector::sectorAllCalibrated( int per );
    double getCalOutput(int per) const; // returns sector output 
    double showprice(int per); // returns sector aggregate price
    void emission(int per); // sum subsector emissions
    void indemission( const int per, const vector<Emcoef_ind>& emcoef_ind ); // sum subsector indirect emissions
    double showpe_cons(int per); // return sector primary energy consumption
    void sumInput(int per); // sums subsector primary and final energy consumption
    double getInput(int per); // return sector energy consumption
    virtual void outputfile(const string& regname ); // write out sector result to file
    void MCoutput_subsec(const string& regname ); // calls write for subsector 
    virtual void MCoutput(const string& regname ); // write out sector result to file
    void subsec_outfile(const string& regname );  // call fn to write subsector output
    double showcarbontaxpaid(int per); // return total sector carbon taxes paid
    map<string, double> getfuelcons(int per); // get fuel consumption map
    double getfuelcons_second(int per,string key); // get second of fuel consumption map
    void clearfuelcons(int per);  //  clears the fuelcons map in summary
    map<string, double> getemission(int per);// get ghg emissions map in summary object 
    map<string, double> getemfuelmap(int per);// get ghg emissions map in summary object
    void updateSummary(const int per);  //  update summaries for reporting
    void addToDependencyGraph( ostream& outStream, const int period );
};

#endif // _SECTOR_H_
