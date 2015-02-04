Flow Management EPD
====================

This is a plugin component for EPD
----------------------------------

Must be embedded inside an EPD installation.

For ship components, edit <homepath>/epd-ship.properties and set the plugin properties
For ship components, edit <homepath>/epd-ship.properties and set the plugin properties

	- epd.plugin_components       - Add the component name (name reused for class)
	- epd.plugin_classpath        - Add this jar file to the path. Delimit with : or ; depending on platform
	- component.name.class        - Add the class name of the component

## Components


### epd.ship.monalisa.tvp

Implement the Tactical Voyage Plan endpoint in the ship.

Implementing class :
     **dk.dma.epd.ship.monalisa2.TacticalVoyagePlanExchangeHandler**
