<?php
include("includes/header.php");
include("includes/contentstart.php");
?>

<p>When you play Encircle, you’re helping scientific research! Some important areas of research that rely heavily on image segmentation:</p>
<ul>
  <li><em>Neuroscience</em>, including the emerging field of connectomics. See, for example, <a href="http://hebb.mit.edu/seunglab/research">Sebastian Seung’s group at MIT</a></li>
  <li><em>Developmental biology</em>, such as research about cell shape change in fruit fly embryos. See, for example, <a href="http://www.molbio1.princeton.edu/wieschaus/">Eric Wieschaus’s group at Princeton</a> and <a href="https://wiki.med.harvard.edu/SysBio/Megason/Research">Sean Megason’s lab at Harvard</a>.</li>
  <li>And many areas outside of the life sciences, such as object detection and video compression.</li>
</ul>


<h2>Image segmentation</h2>
<p>In many tasks involving automated analysis of images, a crucial first step in the pipeline is often <a href="http://en.wikipedia.org/wiki/Image_segmentation">image segmentation</a>. Unfortunately, like many other image processing tasks, image segmentation is far from a solved problem, and modern segmentation algorithms typically do not perform satisfactorily, especially for hard data sets. If an algorithm performs poorly, a huge number of person-hours is required to repair the dataset. In fact, even if an algorithm performs well, a very large number of hours is still needed to validate the computer-generated segmentation. Encircle addresses both reliable segmentation and validation.</p>

<div class="center"><img src="/img/embryo.png" alt="Image segmentation" width="100%"></div>

<h2>Crowdsourcing</h2>
<p>The difficulty of such problems has led researchers to turn to crowdsourcing, in which tasks are completed in small pieces by a large number of (potentially anonymous) people on the internet. One crowdsourcing approach is to embed a useful task within a game; Encircle employs this type of crowdsourcing.</p>

<p>Using crowdsourcing for image segmentation is difficult because the crowd is not ideally set up to trace boundaries in images: they are typically untrained, the task is tedious, and the standard keyboard and mouse interface is not ideal for drawing boundaries. Encircle tackles the task of crowdsourcing for image segmentation in a novel way.</p>

<h2>How we collect data</h2>
<p>Encircle is not a game in which you perform image segmentation directly while playing. Instead, you only provide information that is partially useful for segmentation... but this way we have a lot more freedom to design the game so that it’s fun! The key idea behind using Encircle for image segmentation is that we show the users small pieces of the images, called clusters, and the users respond with the number of segments in the cluster. We are currently performing computational experiments to prove that this information is sufficient for producing reliable segmentations, and preliminary results are positive. Of course, the gameplay of Encircle is very far from just looking at clusters and typing in numbers. But we’ll leave the details to the gameplay section and tutorials.</p>

<?php
include("includes/contentend.php");
include("includes/footer.php");
?>
