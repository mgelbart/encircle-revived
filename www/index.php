<?php
include("includes/header.php");

/*
 *  Check the access code and either serve the applet
 *  or the front page.
 */

if(array_key_exists("ac", $_GET)) {
  $received_code = $_GET["ac"];
} else {
  $received_code = "";
}

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

  <div id="applet">
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
             archive = 'aoi8888.jar'
	     width = '650'
	     height = '650'>The Game</applet>
  </div>

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
    <p>Encircle is currently in private beta.</p>
    </div>

    <div id="play">
    <p>Please enter your access code to <em>play</em> the game:</p>

    <form name="input" method="get">
      Access code: <input type="text" name="ac" /><br/>
      <input type="submit" value="Play!" id="playbutton" />
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

<?php
//phpinfo();

include("includes/footer.php");
?>
