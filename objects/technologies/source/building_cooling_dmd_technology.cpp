/*! 
* \file building_cooling_dmd_technology.cpp
* \ingroup CIAM
* \brief The building cooling service demand technology.
* \author Steve Smith
* \date $Date$
* \version $Revision$
*/

// Standard Library headers
#include "util/base/include/definitions.h"
#include <string>
#include <cassert>

// User headers
#include "technologies/include/building_cooling_dmd_technology.h"
#include "util/base/include/xml_helper.h"
#include "containers/include/scenario.h"
#include "marketplace/include/marketplace.h"
#include "containers/include/iinfo.h"

using namespace std;

extern Scenario* scenario;
// static initialize.
const string BuildingCoolingDmdTechnology::XML_NAME1D = "coolingservice";

// Technology class method definition

/*! 
 * \brief Constructor.
 * \param aName Technology name.
 * \param aYear Technology year.
 */
BuildingCoolingDmdTechnology::BuildingCoolingDmdTechnology( const string& aName, const int aYear )
:BuildingHeatCoolDmdTechnology( aName, aYear )
{
    coolingDegreeDays = 0;
}

//! Clone Function. Returns a deep copy of the current technology.
BuildingCoolingDmdTechnology* BuildingCoolingDmdTechnology::clone() const {
    return new BuildingCoolingDmdTechnology( *this );
}

/*! \brief Get the XML node name for output to XML.
*
* This public function accesses the private constant string, XML_NAME.
* This way the tag is always consistent for both read-in and output and can be easily changed.
* This function may be virtual to be overriden by derived class pointers.
* \author Josh Lurz, James Blackwood
* \return The constant XML_NAME.
*/
const std::string& BuildingCoolingDmdTechnology::getXMLName1D() const {
	return XML_NAME1D;
}

/*! \brief Get the XML node name in static form for comparison when parsing XML.
*
* This public function accesses the private constant string, XML_NAME.
* This way the tag is always consistent for both read-in and output and can be easily changed.
* The "==" operator that is used when parsing, required this second function to return static.
* \note A function cannot be static and virtual.
* \author Josh Lurz, James Blackwood
* \return The constant XML_NAME as a static.
*/
const std::string& BuildingCoolingDmdTechnology::getXMLNameStatic1D() {
	return XML_NAME1D;
}

/*! 
* \brief Perform initializations that only need to be done once per period.
* \param aRegionName Region name.
* \param aSectorName Sector name, also the name of the product.
* \param aSubsectorInfo Parent information container.
* \param aDemographics Regional demographics container.
* \param aPeriod Model period.
*/
void BuildingCoolingDmdTechnology::initCalc( const string& aRegionName,
                                             const string& aSectorName,
                                             const IInfo* aSubsectorInfo,
                                             const Demographic* aDemographics,
                                             const int aPeriod )
{
    coolingDegreeDays = aSubsectorInfo->getDouble( "coolingDegreeDays", true );
    BuildingHeatCoolDmdTechnology::initCalc( aRegionName, aSectorName,
                                             aSubsectorInfo, aDemographics, aPeriod );    
}

/*! \brief Determine sign of internal gains
*
* For cooling sectors internal gains add to demand, so sign is positive
*
* \author Steve Smith
*/
double BuildingCoolingDmdTechnology::getInternalGainsSign() const {
    return +1;    
}
 
//! Demand function prefix.
/*! Defines the demand function, exclusive of demand and share . 
* \author Steve Smith
* \param regionName name of the region
* \param period Model period
*/
double BuildingCoolingDmdTechnology::getDemandFnPrefix( const string& regionName, const int period )  {
Marketplace* marketplace = scenario->getMarketplace();
    
    double priceRatio = ( period > 1 ) ? 
        marketplace->getPrice( fuelname, regionName, period ) / 
        marketplace->getPrice( fuelname, regionName, 1 ) : 1;
    
    double prefixValue =    saturation * aveInsulation * floorToSurfaceArea * 
                            coolingDegreeDays * pow( priceRatio, priceElasticity );
    
    // Make sure and do not return zero
    return ( prefixValue > 0 ) ? prefixValue : 1;
}

