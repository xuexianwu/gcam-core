#ifndef _NATIONAL_ACCOUNT_H_
#define _NATIONAL_ACCOUNT_H_
#if defined(_MSC_VER)
#pragma once
#endif

/*
 * LEGAL NOTICE
 * This computer software was prepared by Battelle Memorial Institute,
 * hereinafter the Contractor, under Contract No. DE-AC05-76RL0 1830
 * with the Department of Energy (DOE). NEITHER THE GOVERNMENT NOR THE
 * CONTRACTOR MAKES ANY WARRANTY, EXPRESS OR IMPLIED, OR ASSUMES ANY
 * LIABILITY FOR THE USE OF THIS SOFTWARE. This notice including this
 * sentence must appear on any copies of this computer software.
 * 
 * EXPORT CONTROL
 * User agrees that the Software will not be shipped, transferred or
 * exported into any country or used in any manner prohibited by the
 * United States Export Administration Act or any other applicable
 * export laws, restrictions or regulations (collectively the "Export Laws").
 * Export of the Software may require some form of license or other
 * authority from the U.S. Government, and failure to obtain such
 * export control license may result in criminal liability under
 * U.S. laws. In addition, if the Software is identified as export controlled
 * items under the Export Laws, User represents and warrants that User
 * is not a citizen, or otherwise located within, an embargoed nation
 * (including without limitation Iran, Syria, Sudan, Cuba, and North Korea)
 *     and that User is not otherwise prohibited
 * under the Export Laws from receiving the Software.
 * 
 * All rights to use the Software are granted on condition that such
 * rights are forfeited if User fails to comply with the terms of
 * this Agreement.
 * 
 * User agrees to identify, defend and hold harmless BATTELLE,
 * its officers, agents and employees from all liability involving
 * the violation of such Export Laws, either directly or indirectly,
 * by User.
 */

/*! 
* \file national_account.h
* \ingroup Objects
* \brief The NationalAccount class header file.
* \author Pralit Patel
* \author Sonny Kim
*/

#include <vector>
#include <string>
#include <xercesc/dom/DOMNode.hpp>
#include "util/base/include/ivisitable.h"

class Tabs;
/*! 
* \ingroup Objects
* \brief A container of national accounts information for the Region.
* \details TODO
* \author Pralit Patel, Sonny Kim
* \todo Lots of documentation!
*/

class NationalAccount: public IVisitable
{
    friend class IVisitor;
    friend class XMLDBOutputter;
public:
    enum AccountType {
        GDP,
        RETAINED_EARNINGS,
        SUBSIDY,
        CORPORATE_PROFITS,
        CORPORATE_RETAINED_EARNINGS,
        CORPORATE_INCOME_TAXES,
        CORPORATE_INCOME_TAX_RATE,
        PERSONAL_INCOME_TAXES,
        INVESTMENT_TAX_CREDIT,
        DIVIDENDS,
        LABOR_WAGES,
        LAND_RENTS,
        TRANSFERS,
        SOCIAL_SECURITY_TAX,
        INDIRECT_BUSINESS_TAX,
        GNP,
        GNP_VA,
        CONSUMPTION,
        GOVERNMENT,
        INVESTMENT,
        NET_EXPORT,
        EXCHANGE_RATE,
        ANNUAL_INVESTMENT,
        // Insert new values before END marker.
        END
    };
    NationalAccount();
    static const std::string& getXMLNameStatic();
    void XMLParse( const xercesc::DOMNode* node );
    void toDebugXML( const int period, std::ostream& out, Tabs* tabs ) const;
    void reset();
    void addToAccount( const AccountType aType, const double aValue );
    void setAccount( const AccountType aType, const double aValue );
    double getAccountValue( const AccountType aType ) const;
    void csvSGMOutputFile( std::ostream& aFile, const int period ) const;
    void accept( IVisitor* aVisitor, const int aPeriod ) const;
private:
    const std::string& enumToName( const AccountType aType ) const;
    const std::string& enumToXMLName( const AccountType aType ) const;
    //! Vector to hold national account values
    std::vector<double> mAccounts;
};

#endif // _NATIONAL_ACCOUNT_H_
