<?php
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

$servegame=in_array($received_code, $acceptable_codes);

include("includes/header.php");

if ($servegame) {
   //echo "code ok";
   // write info about the access to a file
   $t = time();
   $fname = "accesses/".$t."_".$received_code."_".rand();
   $f = fopen($fname, 'w');
   if ($f) {
     fwrite($f, $t);
     fwrite($f, " ".$received_code);
     if(array_key_exists("HTTP_REFERER", $_SERVER))
       fwrite($f, " ".$_SERVER["HTTP_REFERER"]);
     fwrite($f, " ".$_SERVER["HTTP_USER_AGENT"]);
     fwrite($f, " ".$_SERVER["REMOTE_ADDR"]);
     fwrite($f, "\n");
     fclose($f);
   }
?>

  <div id="game-container">

<?php
$w=0;
$h=0;
if(array_key_exists("width", $_GET)
    && array_key_exists("height", $_GET)) {
  $w=$_GET["width"];
  $h=$_GET["height"];
}
// use defaults if width/height not reasonably set
if($w<300 || $h<300) {
  $w=800;
  $h=600;
}
?>
     <applet id='app'
     	     code = 'aoi.App' 
             archive = 'aoi8888.jar'
	     width = '<?php echo $w; ?>'
	     height = '<?php echo $h; ?>'>You need a Java plug-in to play the game.</applet>
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
      <input type="submit" value="Play!" id="playbutton" onclick="setappsize()" />
      <input type="hidden" value="800" name="width" id="dynwidth" />
      <input type="hidden" value="600" name="height" id="dynheight" />
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
