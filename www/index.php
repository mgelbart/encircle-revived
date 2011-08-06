<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"> 
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

  <head>
    <meta http-equiv="content-type" content="text/html; charset=utf-8" />
    <title>Encricle</title>
    <meta name="Description" content="Encircle --- the game" /> 
    <meta name="robots" content="all, index, follow" />
    <link rel="stylesheet" href="style.css" type="text/css" />
  </head>

  <body>

<?php

/*
 *  Check the access code and either server the applet
 *  or the front page.
 */

$received_code = $_GET["ac"];
$acceptable_codes = array("jg", "mg", "hetky", "internaltest");

if (in_array($received_code, $acceptable_codes)) {
   //echo "code ok";
   // write info about the access to a file
   $t = time();
   $fname = "accesses/".$t."_".$received_code."_".rand();
   $f = fopen($fname, 'w');
   if ($f) {
     fwrite($f, $t);
     fwrite($f, " ".$received_code);
     fwrite($f, " ".$_SERVER["HTTP_REFERER"]);
     fwrite($f, " ".$_SERVER["HTTP_USER_AGENT"]);
     fwrite($f, " ".$_SERVER["REMOTE_ADDR"]);
     fwrite($f, "\n");
     fclose($f);
   }
?>

<!--
    <h1>The applet</h1>
    <script src="http://www.java.com/js/deployJava.js"></script>
    <script> 
      var attributes = { code:'aoi.App',  width:700, height:700} ; 
      var parameters = {jnlp_href: 'aoi-applet.jnlp'} ; 
      deployJava.runApplet(attributes, parameters, '1.6'); 
    </script>
-->

     <applet code = 'aoi.App' 
             archive = 'aoi8888.jar', 
             width = 700, 
             height = 700 />


<?php
} else {
   //echo "code not ok";
?>
<!--
  <div id="header">
    <h1>Encircle</h1>
    <h2>the game that will segment your world</h2>
  </div>
-->

  <div id="container">
  <div id="content">

    <h1>Encircle</h1>
    <h2>the game that will segment your world</h2>

    <div id="beta">
    <p>Encricle is currently in private beta.</p>
    </div>

    <div id="play">
    <p>Please enter your access code to <em>play</em> the game:</p>

    <form name="input" method="get">
      Access code: <input type="text" name="ac" />
      <input type="submit" value="Go!" />
    </form>
    </div>

<?php if(array_key_exists("ac", $_GET)) { ?>
    <div id="incorrectcode">
      <p>Access code <em><?php echo $_GET["ac"]; ?></em> was not recognized.</p>
      <p>You may <em>register</em> to receive an access code.</p>
    </div>
<?php } ?>

    <div id="register">
    <p>Or <em>register</em> to receive an access code as soon as we can accomodate more beta testers:</p>

    <form>
      email: <input type="text" name="email" />
      <input type="submit" value="Register" />
    </form>
    </div>

<?php
if(array_key_exists("email", $_GET)) {
?>
    <div id="registered">
<?php
   // write the email to a file.
   $t = time();
   $fname = "registered/".$t."_".rand();
   $f = fopen($fname, 'w');
   if ($f) {
     fwrite($f, "$t ".$_GET["email"]."\n");
     fclose($f);
?>
      <p>Thank you for registering <em><?php echo $_GET["email"]; ?></em>.</p>
      <p>We will contact with an access code as soon as we can.</p>
<?php 
   } else {
?>
      <p>Sorry. We were unable to register <em><?php echo $_GET["email"]; ?></em>.</p>
      <p>Please try again later.</p>
<?php
   }
?>
    </div>
<?php
}
?>

<?php
}
?>

  </div>
  </div>
  <?php //phpinfo(); ?>

  <div id="bkgcredits">
    <p>Background image credits: NASA, ESA, and M. Livio and the Hubble Heritage Team (STSci/Aura)</p>
  </body>

</html>
