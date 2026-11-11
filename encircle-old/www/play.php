<?php

include("includes/header.php");

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
include("includes/footer.php");
?>
