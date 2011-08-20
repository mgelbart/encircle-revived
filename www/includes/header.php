<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"> 
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

  <head>
    <meta http-equiv="content-type" content="text/html; charset=utf-8" />
    <title>Encircle</title>
    <meta name="Description" content="Encircle --- the game" /> 
    <meta name="robots" content="all, index, follow" />
    <link rel="stylesheet" href="css/style.css" type="text/css" />
    <?php include("ganalytics.php"); ?>
    <script type="text/javascript"> 
function setappsize() {
  min_width = 300;
  min_height = 300;
  frame_width = 0;
  frame_height = 0;
  if(parseInt(navigator.appVersion) > 3) {
    if(navigator.appName=='Netscape') {
      frame_width = window.innerWidth;
      frame_height = window.innerHeight;
    }
    if (navigator.appName.indexOf('Microsoft') != -1) {
      frame_width = document.body.offsetWidth;
      frame_height = document.body.offsetHeight;
    }
  }
  frame_width *= 0.95;
  frame_height *= 0.95;
  applet_width = frame_width > min_width ? frame_width : min_width;
  applet_height = frame_height > min_height ? frame_height : min_height;
  document.getElementById('dynwidth').setAttribute('value', Math.round(applet_width));
  document.getElementById('dynheight').setAttribute('value', Math.round(applet_height));
}
</script>

  </head>

  <body>
