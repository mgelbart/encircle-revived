<?php
include("includes/header.php");
include("includes/contentstart.php");
?>

    <div id="play">

    <p>Best your opponents, contribute to science.</p>
    <p>Introducing Encircle, a game that’s fun to play, and lets you contribute to science at the same time. See the <a href="/research.php">research</a> page to see how.</p>

    <form action="/play.php" name="input" method="get">
      <input type="image" src="img/play-btn.png" id="playbutton" onclick="setappsize()" />
<!--
      <input type="submit" value="Play!" id="playbutton" onclick="setappsize()" />
-->
      <input type="hidden" value="800" name="width" id="dynwidth" />
      <input type="hidden" value="600" name="height" id="dynheight" />
    </form>
    </div>

    <div id="register">
    <p>Or <em>register</em> to stay posted on the project status.</p>

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
      <p>We will contact you when major updates to the game are released.</p>
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
//phpinfo();

include("includes/contentend.php");
include("includes/footer.php");
?>
